/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.data.repository

import android.util.Log
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.nohex.itur.core.domain.id.IturActivityId
import com.nohex.itur.core.domain.id.UserId
import com.nohex.itur.core.model.IturActivity
import com.nohex.itur.core.model.IturActivityStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date
import javax.inject.Inject

private const val TAG = "FBActivityRepo"

class FirebaseActivityRepository
@Inject
constructor(
    firestore: FirebaseFirestore,
) : ActivityRepository {
    private val activitiesCollection = firestore.collection("activities")

    override suspend fun getActivity(activityId: IturActivityId): DataResult<IturActivity> {
        val reference = activitiesCollection.document(activityId.value)

        return try {
            withContext(Dispatchers.IO) {
                // Fetch the document.
                val snapshot = reference.get().await()

                if (!snapshot.exists()) {
                    return@withContext DataResult.NotFound(activityId.value)
                }

                // Convert to domain object.
                val activity = snapshot.toObject(IturActivityDTO::class.java)
                    ?.let { dto ->
                        IturActivity(
                            id = IturActivityId(dto.id),
                            organizerId = UserId(dto.organizerId),
                            createdOn = dto.createdOn,
                            participantIds = dto.participantIds.map { UserId(it) },
                        )
                    }

                // Convert to result.
                activity?.let { DataResult.Success(it) }
                    ?: DataResult.Error("DTO conversion failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, e.message, e)
            DataResult.Error(e.message ?: "Undetermined error")
        }
    }

    override suspend fun getActivities(filter: ActivityFilter): DataResult<List<IturActivity>> {
        val query = when (filter) {
            is ActivityFilter.ByOrganizer -> {
                activitiesCollection
                    .whereEqualTo("organizerId", filter.organizerId.value)
            }

            is ActivityFilter.OngoingByOrganizer -> {
                activitiesCollection
                    .whereEqualTo("organizerId", filter.organizerId.value)
                    .whereEqualTo("status", IturActivityStatus.ONGOING.name)
            }
        }

        return try {
            withContext(Dispatchers.IO) {
                val activities = query.get()
                    .await()
                    .toObjects(IturActivityDTO::class.java)
                    .filter { !it.id.isEmpty() }
                    .map { dto ->
                        Log.d(TAG, "Found activity ${dto.id}")
                        IturActivity(
                            id = IturActivityId(dto.id),
                            organizerId = UserId(dto.organizerId),
                            createdOn = dto.createdOn,
                            participantIds = dto.participantIds.map { UserId(it) },
                        )
                    }

                DataResult.Success(activities)
            }
        } catch (e: Exception) {
            Log.e(TAG, e.message, e)
            DataResult.Error(e.message ?: "Undetermined error")
        }
    }

    override suspend fun updateActivityStatus(
        id: IturActivityId,
        newStatus: IturActivityStatus,
    ): DataResult<IturActivity> {
        val reference = activitiesCollection.document(id.value)

        return try {
            withContext(Dispatchers.IO) {
                // Update the activity status.
                reference.update("status", newStatus.name).await()

                // Return the updated activity.
                getActivity(id)
            }
        } catch (e: Exception) {
            Log.e(TAG, e.message, e)
            DataResult.Error(e.message ?: "Undetermined error")
        }
    }

    override suspend fun createActivity(organizerId: UserId): DataResult<IturActivity> {
        // Generate a new document reference.
        val reference = activitiesCollection.document()

        // Create the activity.
        val newActivity = IturActivity(
            id = IturActivityId(reference.id),
            organizerId = organizerId,
            createdOn = Calendar.getInstance().time,
            // The activity is ongoing.
            status = IturActivityStatus.ONGOING,
            // Add the organizer as a participant.
            participantIds = listOf(organizerId),
        )

        // Serialise manually to avoid the gotchas related to value objects.
        return try {
            withContext(Dispatchers.IO) {
                // Store the activity.
                reference
                    .set(
                        IturActivityDTO(
                            id = newActivity.id.value,
                            organizerId = newActivity.organizerId.value,
                            createdOn = newActivity.createdOn,
                            participantIds = newActivity.participantIds.map { it.value },
                        ),
                    )
                    .await()

                // Add the organiser as a participant.

                // Return the successfully created activity
                DataResult.Success(newActivity)
            }
        } catch (e: Exception) {
            Log.e("FirestoreActivityRepo", "Failed to create the activity", e)
            throw e
        }
    }

    override suspend fun deleteActivity(activityId: IturActivityId): DataResult<IturActivity> {
        val reference = activitiesCollection.document(activityId.value)

        return try {
            withContext(Dispatchers.IO) {
                // Retrieve the document.
                val snapshot = reference.get().await()
                if (snapshot.exists()) {
                    // Delete the document.
                    reference.delete().await()
                }

                snapshot.toObject(IturActivityDTO::class.java)?.toDomain()?.let {
                    DataResult.Success(it)
                } ?: DataResult.NotFound(activityId.value)
            }
        } catch (e: Exception) {
            DataResult.Error(e.message ?: "Failed to delete activity ${activityId.value}")
        }
    }

    private suspend fun performDataOperation(
        activityId: IturActivityId,
        operationName: String,
        dataOperation: (DocumentReference) -> Unit,
        needsRefresh: Boolean = true,
    ): DataResult<IturActivity> {
        val reference = activitiesCollection.document(activityId.value)

        return try {
            withContext(Dispatchers.IO) {
                // Retrieve the document.
                var snapshot = reference.get().await()
                if (snapshot.exists()) {
                    // Delete the document.
                    dataOperation(reference)

                    if (needsRefresh) {
                        snapshot = reference.get().await()
                    }
                }

                snapshot.toObject(IturActivityDTO::class.java)?.toDomain()?.let {
                    DataResult.Success(it)
                } ?: DataResult.NotFound(activityId.value)
            }
        } catch (e: Exception) {
            DataResult.Error(e.message ?: "Failed to $operationName activity ${activityId.value}")
        }
    }

    override suspend fun requestAttention(activityId: IturActivityId, userId: UserId) {
        try {
            withContext(Dispatchers.IO) {
                activitiesCollection.document(activityId.value)
                    .update("attentionRequests", FieldValue.arrayUnion(userId.value))
                    .await()
                Log.d(TAG, "User ${userId.value} requested attention in activity ${activityId.value}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request attention for activity ${activityId.value}", e)
            throw e
        }
    }

    override suspend fun addParticipant(
        activityId: IturActivityId,
        userId: UserId,
    ): DataResult<IturActivity> = updateParticipants(activityId) { FieldValue.arrayUnion(userId.value) }

    override suspend fun removeParticipant(
        activityId: IturActivityId,
        userId: UserId,
    ): DataResult<IturActivity> = updateParticipants(activityId) { FieldValue.arrayRemove(userId.value) }

    private suspend fun updateParticipants(
        activityId: IturActivityId,
        function: () -> FieldValue,
    ): DataResult<IturActivity> {
        val reference = activitiesCollection.document(activityId.value)

        return try {
            withContext(Dispatchers.IO) {
                // Update the participant's ID.
                reference.update("participantIds", function.invoke()).await()

                reference.get().await().toObject(IturActivityDTO::class.java)?.toDomain()
                    ?.let { updatedActivity ->
                        DataResult.Success(updatedActivity)
                    } ?: DataResult.Error("Document updated but DTO conversion failed")
            }
        } catch (e: Exception) {
            Log.e("FirestoreActivityRepo", "Could not update participant", e)
            DataResult.Error(e.message ?: "")
        }
    }
}

data class IturActivityDTO(
    var id: String = "",
    var organizerId: String = "",
    var participantIds: List<String> = emptyList(),
    var status: String = IturActivityStatus.DRAFT.name,
    var createdOn: Date = Calendar.getInstance().time,
)

private fun IturActivityDTO.toDomain(): IturActivity = IturActivity(
    id = IturActivityId(id),
    status = IturActivityStatus.valueOf(status),
    organizerId = UserId(organizerId),
    participantIds = participantIds.map { UserId(it) },
    createdOn = createdOn,
)
