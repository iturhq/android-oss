/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.data.repository

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.nohex.itur.core.data.health.BackendHealthReporter
import com.nohex.itur.core.data.health.NoOpBackendHealthReporter
import com.nohex.itur.core.data.health.observeFirestoreMutation
import com.nohex.itur.core.domain.id.IturActivityId
import com.nohex.itur.core.domain.id.UserId
import com.nohex.itur.core.model.Broadcast
import com.nohex.itur.core.model.IturActivity
import com.nohex.itur.core.model.IturActivityStatus
import com.nohex.itur.core.model.ParticipantSignal
import dagger.Lazy
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
    private val backendHealthReporter: Lazy<BackendHealthReporter>,
) : ActivityRepository {
    constructor(firestore: FirebaseFirestore) : this(
        firestore,
        Lazy { NoOpBackendHealthReporter },
    )

    constructor(
        firestore: FirebaseFirestore,
        backendHealthReporter: BackendHealthReporter,
    ) : this(firestore, Lazy { backendHealthReporter })

    private val activitiesCollection = firestore.collection(FirestoreCollections.ACTIVITIES)
    private val usersCollection = firestore.collection(FirestoreCollections.USERS)
    val participantSignalRepository: ParticipantSignalRepository = FirebaseParticipantSignalRepository(
        activitiesCollection = activitiesCollection,
        backendHealthReporter = backendHealthReporter,
    )

    override suspend fun getActiveActivityId(userId: UserId): DataResult<IturActivityId?> = try {
        withContext(Dispatchers.IO) {
            val snapshot = usersCollection.document(userId.value).get().await()
            val activeActivityId = if (snapshot.exists()) snapshot.getString("activeActivityId") else null
            DataResult.Success(activeActivityId?.let { IturActivityId(it) })
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to read active-activity record for ${userId.value}", e)
        DataResult.Error(e.message ?: "Failed to read active-activity record")
    }

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
                val activity = snapshot.toObject(IturActivityDTO::class.java)?.toDomain()

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

            is ActivityFilter.OngoingByParticipant -> {
                activitiesCollection
                    .whereArrayContains("participantIds", filter.participantId.value)
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
                        dto.toDomain()
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
                val updates = mutableMapOf<String, Any>("status" to newStatus.name)
                if (newStatus == IturActivityStatus.FINISHED || newStatus == IturActivityStatus.CANCELLED) {
                    updates["finishedOn"] = FieldValue.serverTimestamp()
                    updates["participantSignals"] = emptyMap<String, String>()
                    updates["attentionRequests"] = emptyList<String>()
                }
                backendHealthReporter.get().observeFirestoreMutation {
                    reference.update(updates).await()
                }

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

        return try {
            withContext(Dispatchers.IO) {
                // Store the activity.
                backendHealthReporter.get().observeFirestoreMutation {
                    reference
                        .set(newActivity.toDto())
                        .await()
                }

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
                    backendHealthReporter.get().observeFirestoreMutation {
                        reference.delete().await()
                    }
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
        participantSignalRepository
            .setParticipantSignal(activityId, userId, ParticipantSignal.NEEDS_HELP)
            .requireSuccess()
    }

    override suspend fun addParticipant(
        activityId: IturActivityId,
        userId: UserId,
    ): DataResult<IturActivity> = updateParticipants(activityId) { FieldValue.arrayUnion(userId.value) }

    override suspend fun removeParticipant(
        activityId: IturActivityId,
        userId: UserId,
    ): DataResult<IturActivity> {
        val reference = activitiesCollection.document(activityId.value)
        return try {
            withContext(Dispatchers.IO) {
                backendHealthReporter.get().observeFirestoreMutation {
                    reference.update(
                        FieldPath.of("participantSignals", userId.value),
                        FieldValue.delete(),
                        "participantIds",
                        FieldValue.arrayRemove(userId.value),
                        "attentionRequests",
                        FieldValue.arrayRemove(userId.value),
                    ).await()
                }
                reference.get().await().toObject(IturActivityDTO::class.java)?.toDomain()
                    ?.let { DataResult.Success(it) }
                    ?: DataResult.Error("Document updated but DTO conversion failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not remove participant", e)
            DataResult.Error(e.message ?: "")
        }
    }

    private suspend fun updateParticipants(
        activityId: IturActivityId,
        function: () -> FieldValue,
    ): DataResult<IturActivity> {
        val reference = activitiesCollection.document(activityId.value)

        return try {
            withContext(Dispatchers.IO) {
                // Update the participant's ID.
                backendHealthReporter.get().observeFirestoreMutation {
                    reference.update("participantIds", function.invoke()).await()
                }

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

    override suspend fun getBroadcastsSince(activityId: IturActivityId, since: Date?): List<Broadcast> {
        var query: Query = activitiesCollection.document(activityId.value)
            .collection("broadcasts")
            .orderBy("sentOn")
        if (since != null) {
            query = query.whereGreaterThan("sentOn", Timestamp(since))
        }

        return try {
            withContext(Dispatchers.IO) {
                query.get().await().toObjects(BroadcastDTO::class.java).mapNotNull { it.toDomain() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch broadcasts for activity ${activityId.value}", e)
            throw e
        }
    }
}

private class FirebaseParticipantSignalRepository(
    private val activitiesCollection: CollectionReference,
    private val backendHealthReporter: Lazy<BackendHealthReporter>,
) : ParticipantSignalRepository {
    override suspend fun setParticipantSignal(
        activityId: IturActivityId,
        userId: UserId,
        signal: ParticipantSignal,
    ): DataResult<IturActivity> = updateParticipantSignal(activityId, userId, signal)

    override suspend fun clearParticipantSignal(
        activityId: IturActivityId,
        userId: UserId,
    ): DataResult<IturActivity> = updateParticipantSignal(activityId, userId, null)

    @Suppress("TooGenericExceptionCaught")
    private suspend fun updateParticipantSignal(
        activityId: IturActivityId,
        userId: UserId,
        signal: ParticipantSignal?,
    ): DataResult<IturActivity> {
        val reference = activitiesCollection.document(activityId.value)
        return try {
            withContext(Dispatchers.IO) {
                updateParticipantSignal(reference, activityId, userId, signal)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update participant signal in activity ${activityId.value}", e)
            DataResult.Error(e.message ?: "Failed to update participant signal")
        }
    }

    private suspend fun updateParticipantSignal(
        reference: DocumentReference,
        activityId: IturActivityId,
        userId: UserId,
        signal: ParticipantSignal?,
    ): DataResult<IturActivity> {
        val before = reference.get().await().toObject(IturActivityDTO::class.java)
            ?: return DataResult.NotFound(activityId.value)
        val activity = before.toDomain()
        return when {
            !activity.canSignal(userId) ->
                DataResult.Error("Only a current participant can change their safety signal")

            before.alreadyStores(userId, signal) -> DataResult.Success(activity)

            else -> {
                backendHealthReporter.get().observeFirestoreMutation {
                    reference.update(
                        FieldPath.of("participantSignals", userId.value),
                        signal?.name ?: FieldValue.delete(),
                        "attentionRequests",
                        FieldValue.arrayRemove(userId.value),
                    ).await()
                }
                reference.get().await().toObject(IturActivityDTO::class.java)?.toDomain()
                    ?.let { DataResult.Success(it) }
                    ?: DataResult.Error("Signal updated but DTO conversion failed")
            }
        }
    }
}

private fun IturActivity.canSignal(userId: UserId): Boolean {
    return status == IturActivityStatus.ONGOING &&
        userId != organizerId &&
        userId in participantIds
}

private fun IturActivityDTO.alreadyStores(userId: UserId, signal: ParticipantSignal?): Boolean {
    val storedSignal = participantSignals[userId.value]
    val hasLegacyRequest = userId.value in attentionRequests
    return storedSignal == signal?.name && !hasLegacyRequest
}

private fun DataResult<IturActivity>.requireSuccess() {
    when (this) {
        is DataResult.Success -> Unit
        is DataResult.NotFound -> error("Activity $id not found")
        is DataResult.Error -> error(message)
    }
}

data class IturActivityDTO(
    var id: String = "",
    var organizerId: String = "",
    var participantIds: List<String> = emptyList(),
    var status: String = IturActivityStatus.DRAFT.name,
    var createdOn: Date = Calendar.getInstance().time,
    var startTime: Date = createdOn,
    var finishedOn: Date? = null,
    var listed: Boolean = false,
    var participantSignals: Map<String, String> = emptyMap(),
    // Legacy binary requests are read as NEEDS_HELP until that participant is rewritten/cleared.
    var attentionRequests: List<String> = emptyList(),
)

private fun IturActivityDTO.toDomain(): IturActivity {
    val organizerUserId = UserId(organizerId)
    val participantUserIds = participantIds.map { UserId(it) }
    val signalEligibleIds = participantUserIds.toSet() - organizerUserId
    val signals = attentionRequests
        .map { UserId(it) }
        .filter { it in signalEligibleIds }
        .associateWith { ParticipantSignal.NEEDS_HELP }
        .toMutableMap()
    participantSignals.forEach { (userId, storedSignal) ->
        val participantId = UserId(userId)
        if (participantId in signalEligibleIds) {
            runCatching { ParticipantSignal.valueOf(storedSignal) }
                .getOrNull()
                ?.let { signals[participantId] = it }
        }
    }
    return IturActivity(
        id = IturActivityId(id),
        status = IturActivityStatus.valueOf(status),
        organizerId = organizerUserId,
        participantIds = participantUserIds,
        createdOn = createdOn,
        startTime = startTime,
        finishedOn = finishedOn,
        listed = listed,
        participantSignals = signals,
    )
}

private fun IturActivity.toDto(): IturActivityDTO = IturActivityDTO(
    id = id.value,
    organizerId = organizerId.value,
    participantIds = participantIds.map { it.value },
    status = status.name,
    createdOn = createdOn,
    startTime = startTime,
    finishedOn = finishedOn,
    listed = listed,
    participantSignals = participantSignals.mapKeys { it.key.value }.mapValues { it.value.name },
)

data class BroadcastDTO(
    @DocumentId
    var id: String = "",
    var message: String? = null,
    var sentOn: Date? = null,
)

private fun BroadcastDTO.toDomain(): Broadcast? {
    val message = message ?: return null
    val sentOn = sentOn ?: return null
    return Broadcast(id = id, message = message, sentOn = sentOn)
}
