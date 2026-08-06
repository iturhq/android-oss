/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.data.repository

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.nohex.itur.core.domain.id.IturActivityId
import com.nohex.itur.core.domain.id.UserId
import com.nohex.itur.core.model.Location
import com.nohex.itur.core.model.ParticipantLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

class FirebaseLocationRepository
@Inject
constructor(
    firestore: FirebaseFirestore,
    private val userRepository: UserRepository,
) : LocationRepository {
    private val locationsCollection = firestore.collection(FirestoreCollections.LOCATIONS)
    override suspend fun getForActivity(activityId: IturActivityId): List<ParticipantLocation> = withContext(Dispatchers.IO) {
        val querySnapshot =
            locationsCollection.whereEqualTo("activityId", activityId.value)
                .get()
                .await()

        val dtoList = querySnapshot.toObjects(ParticipantLocationDTO::class.java)

        val userIds = dtoList.map { UserId(it.userId) }.distinct()
        val namesById = if (userIds.isEmpty()) {
            emptyMap()
        } else {
            userRepository.getAll(userIds).associateBy { it.id }
        }

        return@withContext dtoList.map {
            val userId = UserId(it.userId)
            ParticipantLocation(
                activityId = IturActivityId(it.activityId),
                userId = userId,
                userName = namesById[userId]?.name ?: "<Not available>",
                location = Location(
                    latitude = it.location.latitude,
                    longitude = it.location.longitude,
                ),
                recordedAt = it.updatedOn.toDate(),
            )
        }
    }

    override suspend fun removeForActivity(activityId: IturActivityId) {
        withContext(Dispatchers.IO) {
            val querySnapshot = locationsCollection
                .whereEqualTo("activityId", activityId.value)
                .get()
                .await()
            querySnapshot.documents.forEach { it.reference.delete().await() }
            Log.d(
                "FirestoreLocationRepo",
                "Removed ${querySnapshot.size()} location(s) for activity ${activityId.value}",
            )
        }
    }

    override suspend fun updateForParticipant(
        userId: UserId,
        activityId: IturActivityId,
        location: Location,
    ): Unit = withContext(Dispatchers.IO) {
        val querySnapshot = locationsCollection
            .whereEqualTo("activityId", activityId.value)
            .whereEqualTo("userId", userId.value)
            .get()
            .await()

        val firebaseLocation = GeoPoint(location.latitude, location.longitude)

        // If there is no record of this user and activity...
        if (querySnapshot.isEmpty) {
            // ... create new location record...
            val newRecord = ParticipantLocationDTO(
                activityId = activityId.value,
                userId = userId.value,
                location = firebaseLocation,
                updatedOn = Timestamp.now(),
            )
            val newReference = locationsCollection.add(newRecord).await()

            Log.d(
                "FirestoreLocationRepo",
                "Created location ${newReference.id} for user ${userId.value} in activity ${activityId.value}",
            )
        } else {
            // ...otherwise update the existing record.
            val documentId = querySnapshot.documents.first().id
            locationsCollection.document(documentId).update(
                mapOf(
                    "location" to firebaseLocation,
                    "updatedOn" to Timestamp.now(),
                ),
            ).await()

            Log.d(
                "FirestoreLocationRepo",
                "Updated location $documentId for user ${userId.value} in activity ${activityId.value}",
            )
        }
    }
}

data class ParticipantLocationDTO(
    var activityId: String = "",
    var userId: String = "",
    var location: GeoPoint = GeoPoint(0.0, 0.0),
    var updatedOn: Timestamp = Timestamp.now(),
)
