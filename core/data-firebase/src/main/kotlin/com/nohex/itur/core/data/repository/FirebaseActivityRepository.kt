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
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Transaction
import com.google.firebase.functions.FirebaseFunctions
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
private const val ACTIVE_ACTIVITY_ID = "activeActivityId"

class FirebaseActivityRepository
private constructor(
    private val firestore: FirebaseFirestore,
    private val backendHealthReporter: Lazy<BackendHealthReporter>,
    private val transactionExecutor: FirestoreTransactionExecutor,
    private val admissionGateway: ActivityAdmissionGateway,
) : ActivityRepository {
    @Inject
    constructor(
        firestore: FirebaseFirestore,
        backendHealthReporter: Lazy<BackendHealthReporter>,
        functions: FirebaseFunctions,
    ) : this(
        firestore,
        backendHealthReporter,
        FirebaseFirestoreTransactionExecutor(firestore),
        FirebaseFunctionsActivityAdmissionGateway(functions),
    )

    constructor(firestore: FirebaseFirestore) : this(
        firestore,
        Lazy { NoOpBackendHealthReporter },
        FirebaseFirestoreTransactionExecutor(firestore),
        FirebaseFunctionsActivityAdmissionGateway(FirebaseFunctions.getInstance()),
    )

    constructor(
        firestore: FirebaseFirestore,
        functions: FirebaseFunctions,
    ) : this(
        firestore,
        Lazy { NoOpBackendHealthReporter },
        FirebaseFirestoreTransactionExecutor(firestore),
        FirebaseFunctionsActivityAdmissionGateway(functions),
    )

    constructor(
        firestore: FirebaseFirestore,
        backendHealthReporter: BackendHealthReporter,
    ) : this(
        firestore,
        Lazy { backendHealthReporter },
        FirebaseFirestoreTransactionExecutor(firestore),
        FirebaseFunctionsActivityAdmissionGateway(FirebaseFunctions.getInstance()),
    )

    internal constructor(
        firestore: FirebaseFirestore,
        backendHealthReporter: BackendHealthReporter,
        transactionExecutor: FirestoreTransactionExecutor,
        admissionGateway: ActivityAdmissionGateway,
    ) : this(firestore, Lazy { backendHealthReporter }, transactionExecutor, admissionGateway)

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
        if (newStatus == IturActivityStatus.ONGOING) {
            return when (val admitted = admissionGateway.start(id)) {
                is DataResult.Success -> getActivity(admitted.data)
                is DataResult.Error -> admitted
                is DataResult.NotFound -> admitted
            }
        }
        val reference = activitiesCollection.document(id.value)

        return try {
            withContext(Dispatchers.IO) {
                val updated = backendHealthReporter.get().observeFirestoreMutation {
                    transactionExecutor.run { transaction -> transaction.changeStatus(reference, id, newStatus) }
                }
                DataResult.Success(updated.toDomain())
            }
        } catch (e: Exception) {
            Log.e(TAG, e.message, e)
            DataResult.Error(e.message ?: "Undetermined error")
        }
    }

    @Suppress("MaxLineLength")
    override suspend fun createActivity(organizerId: UserId): DataResult<IturActivity> = when (val admitted = admissionGateway.start()) {
        is DataResult.Success -> getActivity(admitted.data)
        is DataResult.Error -> admitted
        is DataResult.NotFound -> admitted
    }

    override suspend fun deleteActivity(activityId: IturActivityId): DataResult<IturActivity> {
        val reference = activitiesCollection.document(activityId.value)

        return try {
            withContext(Dispatchers.IO) {
                val deleted = backendHealthReporter.get().observeFirestoreMutation {
                    transactionExecutor.run { transaction ->
                        val before = transaction.get(reference).toObject(IturActivityDTO::class.java)
                            ?: return@run null
                        val memberIds = if (before.status == IturActivityStatus.ONGOING.name) {
                            (listOf(before.organizerId) + before.participantIds).distinct().map(::UserId)
                        } else {
                            emptyList()
                        }
                        transaction.delete(reference)
                        memberIds.forEach { userId -> transaction.clearExistingReservation(userId) }
                        before
                    }
                }
                deleted?.toDomain()?.let {
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
    ): DataResult<IturActivity> = when (val admitted = admissionGateway.join(activityId)) {
        is DataResult.Success -> getActivity(admitted.data)
        is DataResult.Error -> admitted
        is DataResult.NotFound -> admitted
    }

    override suspend fun removeParticipant(
        activityId: IturActivityId,
        userId: UserId,
    ): DataResult<IturActivity> = when (val departed = admissionGateway.leave(activityId)) {
        is DataResult.Success -> getActivity(departed.data)
        is DataResult.Error -> departed
        is DataResult.NotFound -> departed
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

    private fun Transaction.reserve(userId: UserId, activityId: IturActivityId) {
        set(
            usersCollection.document(userId.value),
            mapOf(ACTIVE_ACTIVITY_ID to activityId.value),
            SetOptions.merge(),
        )
    }

    private fun Transaction.requireReservationAvailable(
        existingActivityId: String?,
        userId: UserId,
        targetActivityId: IturActivityId,
    ) {
        if (existingActivityId == null || existingActivityId == targetActivityId.value) return
        val existing = get(activitiesCollection.document(existingActivityId))
            .toObject(IturActivityDTO::class.java)
        val stillActive = existing?.status == IturActivityStatus.ONGOING.name &&
            (existing.organizerId == userId.value || userId.value in existing.participantIds)
        check(!stillActive) { "User is already active in another activity" }
    }

    private fun Transaction.changeStatus(
        reference: DocumentReference,
        activityId: IturActivityId,
        newStatus: IturActivityStatus,
    ): IturActivityDTO {
        val before = get(reference).toObject(IturActivityDTO::class.java)
            ?: error("Activity ${activityId.value} not found")
        val transition = membershipTransition(before, newStatus)
        val reservations = if (transition.entersOngoing) {
            readReservations(listOf(UserId(before.organizerId)))
        } else {
            emptyMap()
        }
        if (transition.entersOngoing) {
            requireReservationsAvailable(reservations, activityId)
        }

        update(reference, statusUpdates(newStatus))
        applyReservationTransition(transition, activityId)
        return before.withStatus(newStatus)
    }

    private fun membershipTransition(
        before: IturActivityDTO,
        newStatus: IturActivityStatus,
    ): MembershipTransition {
        val wasOngoing = before.status == IturActivityStatus.ONGOING.name
        val willBeOngoing = newStatus == IturActivityStatus.ONGOING
        val members = when {
            !wasOngoing && willBeOngoing ->
                (listOf(before.organizerId) + before.participantIds).distinct().map(::UserId)
            wasOngoing && !willBeOngoing ->
                (listOf(before.organizerId) + before.participantIds).distinct().map(::UserId)
            else -> emptyList()
        }
        return MembershipTransition(members, !wasOngoing && willBeOngoing, wasOngoing && !willBeOngoing)
    }

    @Suppress("MaxLineLength")
    private fun Transaction.readReservations(members: List<UserId>): Map<UserId, String?> = members.associateWith { userId ->
        get(usersCollection.document(userId.value)).getString(ACTIVE_ACTIVITY_ID)
    }

    private fun Transaction.requireReservationsAvailable(
        reservations: Map<UserId, String?>,
        activityId: IturActivityId,
    ) {
        reservations.forEach { (userId, existingActivityId) ->
            requireReservationAvailable(existingActivityId, userId, activityId)
        }
    }

    @Suppress("MaxLineLength")
    private fun statusUpdates(newStatus: IturActivityStatus): Map<String, Any> = mutableMapOf<String, Any>("status" to newStatus.name).apply {
        if (newStatus.isTerminal()) {
            this["finishedOn"] = FieldValue.serverTimestamp()
            this["participantSignals"] = emptyMap<String, String>()
            this["attentionRequests"] = emptyList<String>()
        }
    }

    private fun Transaction.applyReservationTransition(
        transition: MembershipTransition,
        activityId: IturActivityId,
    ) {
        when {
            transition.entersOngoing -> transition.members.forEach { reserve(it, activityId) }
            transition.leavesOngoing -> transition.members.forEach { userId -> clearExistingReservation(userId) }
        }
    }

    private fun Transaction.clearExistingReservation(userId: UserId) {
        update(usersCollection.document(userId.value), ACTIVE_ACTIVITY_ID, null)
    }
}

@Suppress("MaxLineLength")
private fun IturActivityStatus.isTerminal(): Boolean = this == IturActivityStatus.FINISHED || this == IturActivityStatus.CANCELLED

private data class MembershipTransition(
    val members: List<UserId>,
    val entersOngoing: Boolean,
    val leavesOngoing: Boolean,
)

private fun IturActivityDTO.withStatus(newStatus: IturActivityStatus): IturActivityDTO = copy(
    status = newStatus.name,
    finishedOn = if (newStatus.isTerminal()) Date() else finishedOn,
    participantSignals = if (newStatus.isTerminal()) emptyMap() else participantSignals,
    attentionRequests = if (newStatus.isTerminal()) emptyList() else attentionRequests,
)

internal interface FirestoreTransactionExecutor {
    suspend fun <T> run(operation: (Transaction) -> T): T
}

private class FirebaseFirestoreTransactionExecutor(
    private val firestore: FirebaseFirestore,
) : FirestoreTransactionExecutor {
    @Suppress("MaxLineLength")
    override suspend fun <T> run(operation: (Transaction) -> T): T = firestore.runTransaction(Transaction.Function(operation)).await()
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

private fun IturActivity.canSignal(userId: UserId): Boolean = status == IturActivityStatus.ONGOING &&
    userId != organizerId &&
    userId in participantIds

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
