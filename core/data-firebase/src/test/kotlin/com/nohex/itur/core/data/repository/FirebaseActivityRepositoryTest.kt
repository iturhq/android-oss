/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Transaction
import com.nohex.itur.core.data.health.BackendHealthObservation
import com.nohex.itur.core.data.health.BackendHealthReporter
import com.nohex.itur.core.data.health.BackendHealthStatus
import com.nohex.itur.core.data.health.BackendServiceHealth
import com.nohex.itur.core.data.health.BackendServiceIds
import com.nohex.itur.core.data.health.withObservation
import com.nohex.itur.core.domain.id.IturActivityId
import com.nohex.itur.core.domain.id.UserId
import com.nohex.itur.core.model.IturActivity
import com.nohex.itur.core.model.IturActivityStatus
import com.nohex.itur.core.model.ParticipantSignal
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val ORGANIZER_ID = UserId("organizer1")
private val PARTICIPANT_ID = UserId("participant1")
private val ACTIVITY_ID = IturActivityId("TestActivity00000001")

private val ACTIVITY_DTO = IturActivityDTO(
    id = ACTIVITY_ID.value,
    organizerId = ORGANIZER_ID.value,
    participantIds = listOf(ORGANIZER_ID.value, PARTICIPANT_ID.value),
    status = "ONGOING",
)

private class RecordingBackendHealthReporter : BackendHealthReporter {
    val reports = mutableListOf<Pair<String, BackendHealthObservation>>()

    override fun report(serviceId: String, observation: BackendHealthObservation) {
        reports += serviceId to observation
    }
}

private class ImmediateTransactionExecutor(
    private val transaction: Transaction,
) : FirestoreTransactionExecutor {
    override suspend fun <T> run(operation: (Transaction) -> T): T = operation(transaction)
}

private class RecordingAdmissionGateway : ActivityAdmissionGateway {
    var startResult: DataResult<IturActivityId> = DataResult.Error("start not configured")
    var joinResult: DataResult<IturActivityId> = DataResult.Error("join not configured")
    var leaveResult: DataResult<IturActivityId> = DataResult.Error("leave not configured")
    val startCalls = mutableListOf<IturActivityId?>()
    val joinCalls = mutableListOf<IturActivityId>()
    val leaveCalls = mutableListOf<IturActivityId>()

    override suspend fun start(activityId: IturActivityId?): DataResult<IturActivityId> {
        startCalls += activityId
        return startResult
    }

    override suspend fun join(activityId: IturActivityId): DataResult<IturActivityId> {
        joinCalls += activityId
        return joinResult
    }

    override suspend fun leave(activityId: IturActivityId): DataResult<IturActivityId> {
        leaveCalls += activityId
        return leaveResult
    }
}

private fun reservationSnapshot(activeActivityId: String?): DocumentSnapshot = mockk {
    every { getString("activeActivityId") } returns activeActivityId
}

class FirebaseActivityRepositoryTest {
    private val activitiesCollection = mockk<CollectionReference>()
    private val usersCollection = mockk<CollectionReference>()
    private val firestore = mockk<FirebaseFirestore> {
        every { collection(FirestoreCollections.ACTIVITIES) } returns activitiesCollection
        every { collection(FirestoreCollections.USERS) } returns usersCollection
    }
    private val transaction = mockk<Transaction>(relaxed = true)
    private val transactionExecutor = ImmediateTransactionExecutor(transaction)
    private val admissionGateway = RecordingAdmissionGateway()
    private val repository = FirebaseActivityRepository(
        firestore,
        mockk<BackendHealthReporter>(relaxed = true),
        transactionExecutor,
        admissionGateway,
    )

    // --- getActiveActivityId ---

    @Test
    fun `GIVEN a user document with activeActivityId set WHEN getting the active activity THEN returns it`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        every { usersCollection.document(ORGANIZER_ID.value) } returns docRef
        val snapshot = mockk<DocumentSnapshot> {
            every { exists() } returns true
            every { getString("activeActivityId") } returns ACTIVITY_ID.value
        }
        every { docRef.get() } returns successfulTask(snapshot)

        val result = repository.getActiveActivityId(ORGANIZER_ID)

        assertIs<DataResult.Success<IturActivityId?>>(result)
        assertEquals(ACTIVITY_ID, result.data)
    }

    @Test
    fun `GIVEN a user document with no activeActivityId WHEN getting the active activity THEN returns null`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        every { usersCollection.document(ORGANIZER_ID.value) } returns docRef
        val snapshot = mockk<DocumentSnapshot> {
            every { exists() } returns true
            every { getString("activeActivityId") } returns null
        }
        every { docRef.get() } returns successfulTask(snapshot)

        val result = repository.getActiveActivityId(ORGANIZER_ID)

        assertIs<DataResult.Success<IturActivityId?>>(result)
        assertEquals(null, result.data)
    }

    @Test
    fun `GIVEN no user document exists WHEN getting the active activity THEN returns null`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        every { usersCollection.document(ORGANIZER_ID.value) } returns docRef
        val snapshot = mockk<DocumentSnapshot> { every { exists() } returns false }
        every { docRef.get() } returns successfulTask(snapshot)

        val result = repository.getActiveActivityId(ORGANIZER_ID)

        assertIs<DataResult.Success<IturActivityId?>>(result)
        assertEquals(null, result.data)
    }

    @Test
    fun `GIVEN Firestore throws WHEN getting the active activity THEN returns Error rather than propagating`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        every { usersCollection.document(ORGANIZER_ID.value) } returns docRef
        every { docRef.get() } returns failedTask(RuntimeException("offline"))

        val result = repository.getActiveActivityId(ORGANIZER_ID)

        assertIs<DataResult.Error>(result)
        Unit
    }

    // --- getActivity ---

    @Test
    fun `GIVEN an existing document WHEN getting it by ID THEN returns Success with the mapped activity`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        every { activitiesCollection.document(ACTIVITY_ID.value) } returns docRef
        val snapshot = mockk<DocumentSnapshot> {
            every { exists() } returns true
            every { toObject(IturActivityDTO::class.java) } returns ACTIVITY_DTO
        }
        every { docRef.get() } returns successfulTask(snapshot)

        val result = repository.getActivity(ACTIVITY_ID)

        assertIs<DataResult.Success<IturActivity>>(result)
        assertEquals(ACTIVITY_ID, result.data.id)
        assertEquals(ORGANIZER_ID, result.data.organizerId)
    }

    @Test
    fun `GIVEN no document WHEN getting it by ID THEN returns NotFound`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        every { activitiesCollection.document(ACTIVITY_ID.value) } returns docRef
        val snapshot = mockk<DocumentSnapshot> { every { exists() } returns false }
        every { docRef.get() } returns successfulTask(snapshot)

        val result = repository.getActivity(ACTIVITY_ID)

        assertIs<DataResult.NotFound>(result)
        assertEquals(ACTIVITY_ID.value, result.id)
    }

    @Test
    fun `GIVEN a document that fails DTO conversion WHEN getting it by ID THEN returns Error`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        every { activitiesCollection.document(ACTIVITY_ID.value) } returns docRef
        val snapshot = mockk<DocumentSnapshot> {
            every { exists() } returns true
            every { toObject(IturActivityDTO::class.java) } returns null
        }
        every { docRef.get() } returns successfulTask(snapshot)

        val result = repository.getActivity(ACTIVITY_ID)

        assertIs<DataResult.Error>(result)
        Unit
    }

    @Test
    fun `GIVEN Firestore throws WHEN getting an activity THEN returns Error rather than propagating`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        every { activitiesCollection.document(ACTIVITY_ID.value) } returns docRef
        every { docRef.get() } returns failedTask(RuntimeException("offline"))

        val result = repository.getActivity(ACTIVITY_ID)

        assertIs<DataResult.Error>(result)
        assertEquals("offline", result.message)
    }

    // --- getActivities ---

    @Test
    fun `GIVEN ByOrganizer filter WHEN getting activities THEN queries only by organizerId`() = runBlocking {
        val query = mockk<Query>()
        every { activitiesCollection.whereEqualTo("organizerId", ORGANIZER_ID.value) } returns query
        val querySnapshot = mockk<QuerySnapshot> { every { toObjects(IturActivityDTO::class.java) } returns listOf(ACTIVITY_DTO) }
        every { query.get() } returns successfulTask(querySnapshot)

        val result = repository.getActivities(ActivityFilter.ByOrganizer(ORGANIZER_ID))

        assertIs<DataResult.Success<List<IturActivity>>>(result)
        assertEquals(listOf(ACTIVITY_ID), result.data.map { it.id })
    }

    @Test
    fun `GIVEN OngoingByOrganizer filter WHEN getting activities THEN also filters by ONGOING status`() = runBlocking {
        val byOrganizer = mockk<Query>()
        val ongoingQuery = mockk<Query>()
        every { activitiesCollection.whereEqualTo("organizerId", ORGANIZER_ID.value) } returns byOrganizer
        every { byOrganizer.whereEqualTo("status", "ONGOING") } returns ongoingQuery
        val querySnapshot = mockk<QuerySnapshot> { every { toObjects(IturActivityDTO::class.java) } returns listOf(ACTIVITY_DTO) }
        every { ongoingQuery.get() } returns successfulTask(querySnapshot)

        val result = repository.getActivities(ActivityFilter.OngoingByOrganizer(ORGANIZER_ID))

        assertIs<DataResult.Success<List<IturActivity>>>(result)
        assertEquals(1, result.data.size)
    }

    @Test
    fun `GIVEN OngoingByParticipant filter WHEN getting activities THEN queries membership and status`() = runBlocking {
        val byParticipant = mockk<Query>()
        val ongoingQuery = mockk<Query>()
        every {
            activitiesCollection.whereArrayContains(
                "participantIds",
                PARTICIPANT_ID.value,
            )
        } returns byParticipant
        every { byParticipant.whereEqualTo("status", "ONGOING") } returns ongoingQuery
        val querySnapshot = mockk<QuerySnapshot> {
            every { toObjects(IturActivityDTO::class.java) } returns listOf(ACTIVITY_DTO)
        }
        every { ongoingQuery.get() } returns successfulTask(querySnapshot)

        val result = repository.getActivities(
            ActivityFilter.OngoingByParticipant(PARTICIPANT_ID),
        )

        assertIs<DataResult.Success<List<IturActivity>>>(result)
        assertEquals(1, result.data.size)
    }

    @Test
    fun `GIVEN documents with a blank ID WHEN getting activities THEN they are filtered out`() = runBlocking {
        val query = mockk<Query>()
        every { activitiesCollection.whereEqualTo("organizerId", ORGANIZER_ID.value) } returns query
        val blankIdDto = ACTIVITY_DTO.copy(id = "")
        val querySnapshot = mockk<QuerySnapshot> {
            every { toObjects(IturActivityDTO::class.java) } returns listOf(ACTIVITY_DTO, blankIdDto)
        }
        every { query.get() } returns successfulTask(querySnapshot)

        val result = repository.getActivities(ActivityFilter.ByOrganizer(ORGANIZER_ID))

        assertIs<DataResult.Success<List<IturActivity>>>(result)
        assertEquals(1, result.data.size)
    }

    @Test
    fun `GIVEN Firestore throws WHEN getting activities THEN returns Error`() = runBlocking {
        val query = mockk<Query>()
        every { activitiesCollection.whereEqualTo("organizerId", ORGANIZER_ID.value) } returns query
        every { query.get() } returns failedTask(RuntimeException("boom"))

        val result = repository.getActivities(ActivityFilter.ByOrganizer(ORGANIZER_ID))

        assertIs<DataResult.Error>(result)
        Unit
    }

    // --- updateActivityStatus ---

    @Test
    fun `GIVEN a non-terminal status WHEN updating THEN finishedOn is not set`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        val organizerRef = mockk<DocumentReference>()
        val participantRef = mockk<DocumentReference>()
        every { activitiesCollection.document(ACTIVITY_ID.value) } returns docRef
        every { usersCollection.document(ORGANIZER_ID.value) } returns organizerRef
        every { usersCollection.document(PARTICIPANT_ID.value) } returns participantRef
        val updates = slot<Map<String, Any>>()
        every { transaction.update(docRef, capture(updates)) } returns transaction
        val activitySnapshot = mockk<DocumentSnapshot> {
            every { toObject(IturActivityDTO::class.java) } returns ACTIVITY_DTO
        }
        every { transaction.get(docRef) } returns activitySnapshot
        every { transaction.get(organizerRef) } returns reservationSnapshot(ACTIVITY_ID.value)
        every { transaction.get(participantRef) } returns reservationSnapshot(ACTIVITY_ID.value)

        val result = repository.updateActivityStatus(ACTIVITY_ID, IturActivityStatus.READY)

        assertIs<DataResult.Success<IturActivity>>(result)
        assertEquals(setOf("status"), updates.captured.keys)
    }

    @Test
    fun `GIVEN a terminal status WHEN updating THEN finishedOn is also set`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        val organizerRef = mockk<DocumentReference>()
        val participantRef = mockk<DocumentReference>()
        every { activitiesCollection.document(ACTIVITY_ID.value) } returns docRef
        every { usersCollection.document(ORGANIZER_ID.value) } returns organizerRef
        every { usersCollection.document(PARTICIPANT_ID.value) } returns participantRef
        val updates = slot<Map<String, Any>>()
        every { transaction.update(docRef, capture(updates)) } returns transaction
        val activitySnapshot = mockk<DocumentSnapshot> {
            every { toObject(IturActivityDTO::class.java) } returns ACTIVITY_DTO
        }
        every { transaction.get(docRef) } returns activitySnapshot
        every { transaction.get(organizerRef) } returns reservationSnapshot(ACTIVITY_ID.value)
        every { transaction.get(participantRef) } returns reservationSnapshot(ACTIVITY_ID.value)

        val result = repository.updateActivityStatus(ACTIVITY_ID, IturActivityStatus.FINISHED)

        assertIs<DataResult.Success<IturActivity>>(result)
        assertEquals(
            setOf("status", "finishedOn", "participantSignals", "attentionRequests"),
            updates.captured.keys,
        )
        assertEquals(emptyMap<String, String>(), updates.captured["participantSignals"])
        assertEquals(emptyList<String>(), updates.captured["attentionRequests"])
    }

    @Test
    fun `GIVEN a ready activity WHEN starting THEN trusted admission is used and the result is refreshed`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        every { activitiesCollection.document(ACTIVITY_ID.value) } returns docRef
        every { docRef.get() } returns successfulTask(
            mockk {
                every { exists() } returns true
                every { toObject(IturActivityDTO::class.java) } returns ACTIVITY_DTO
            },
        )
        admissionGateway.startResult = DataResult.Success(ACTIVITY_ID)

        val result = repository.updateActivityStatus(ACTIVITY_ID, IturActivityStatus.ONGOING)

        assertIs<DataResult.Success<IturActivity>>(result)
        assertEquals(listOf<IturActivityId?>(ACTIVITY_ID), admissionGateway.startCalls)
        verify(exactly = 0) { transaction.update(any<DocumentReference>(), any<Map<String, Any>>()) }
    }

    @Test
    fun `GIVEN trusted start is rejected WHEN starting THEN its neutral code is preserved`() = runBlocking {
        admissionGateway.startResult = DataResult.Error(
            "Activity start limit reached.",
            DataErrorCode.ACTIVITY_START_LIMIT_REACHED,
        )

        val result = repository.updateActivityStatus(ACTIVITY_ID, IturActivityStatus.ONGOING)

        val error = assertIs<DataResult.Error>(result)
        assertEquals(DataErrorCode.ACTIVITY_START_LIMIT_REACHED, error.code)
        assertEquals(listOf<IturActivityId?>(ACTIVITY_ID), admissionGateway.startCalls)
    }

    @Test
    fun `GIVEN Firestore throws WHEN updating status THEN returns Error`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        every { activitiesCollection.document(ACTIVITY_ID.value) } returns docRef
        every { transaction.get(docRef) } throws RuntimeException("denied")

        val result = repository.updateActivityStatus(ACTIVITY_ID, IturActivityStatus.FINISHED)

        assertIs<DataResult.Error>(result)
        Unit
    }

    // --- createActivity ---

    @Test
    fun `WHEN creating an activity THEN trusted admission creates it and the stored activity is returned`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        every { activitiesCollection.document(ACTIVITY_ID.value) } returns docRef
        every { docRef.get() } returns successfulTask(
            mockk {
                every { exists() } returns true
                every { toObject(IturActivityDTO::class.java) } returns ACTIVITY_DTO
            },
        )
        admissionGateway.startResult = DataResult.Success(ACTIVITY_ID)

        val result = repository.createActivity(ORGANIZER_ID)

        assertIs<DataResult.Success<IturActivity>>(result)
        assertEquals(ACTIVITY_ID, result.data.id)
        assertEquals(listOf<IturActivityId?>(null), admissionGateway.startCalls)
    }

    @Test
    fun `GIVEN trusted create is rejected WHEN creating THEN the structured failure is returned`() = runBlocking {
        admissionGateway.startResult = DataResult.Error(
            "Activity start limit reached.",
            DataErrorCode.ACTIVITY_START_LIMIT_REACHED,
        )

        val result = repository.createActivity(ORGANIZER_ID)

        val error = assertIs<DataResult.Error>(result)
        assertEquals(DataErrorCode.ACTIVITY_START_LIMIT_REACHED, error.code)
        assertEquals(listOf<IturActivityId?>(null), admissionGateway.startCalls)
    }

    @Test
    fun `permission denied mutation reports generic degradation and next mutation recovers`() = runBlocking {
        val reporter = RecordingBackendHealthReporter()
        val healthAwareRepository = FirebaseActivityRepository(
            firestore,
            reporter,
            transactionExecutor,
            RecordingAdmissionGateway(),
        )
        val docRef = mockk<DocumentReference>()
        val organizerRef = mockk<DocumentReference>()
        val participantRef = mockk<DocumentReference>()
        val privateFailure = FirebaseFirestoreException(
            "PERMISSION_DENIED activity=${ACTIVITY_ID.value} user=${PARTICIPANT_ID.value} token=secret",
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
        )
        every { activitiesCollection.document(ACTIVITY_ID.value) } returns docRef
        every { usersCollection.document(ORGANIZER_ID.value) } returns organizerRef
        every { usersCollection.document(PARTICIPANT_ID.value) } returns participantRef
        every { transaction.get(docRef) } throws privateFailure andThen mockk<DocumentSnapshot> {
            every { toObject(IturActivityDTO::class.java) } returns ACTIVITY_DTO
        }
        every { transaction.get(organizerRef) } returns reservationSnapshot(ACTIVITY_ID.value)
        every { transaction.get(participantRef) } returns reservationSnapshot(ACTIVITY_ID.value)

        assertIs<DataResult.Error>(healthAwareRepository.deleteActivity(ACTIVITY_ID))
        assertIs<DataResult.Success<IturActivity>>(healthAwareRepository.deleteActivity(ACTIVITY_ID))

        assertEquals(
            listOf(BackendServiceIds.FIREBASE_FIRESTORE, BackendServiceIds.FIREBASE_FIRESTORE),
            reporter.reports.map(Pair<String, BackendHealthObservation>::first),
        )
        val failure = assertIs<BackendHealthObservation.OperationFailed>(reporter.reports.first().second)
        assertEquals("Cloud Firestore mutation failed", failure.evidence.summary)
        assertNull(failure.evidence.diagnosticTrace)
        assertTrue(!failure.evidence.summary.contains(ACTIVITY_ID.value))
        assertTrue(!failure.evidence.summary.contains(PARTICIPANT_ID.value))
        assertIs<BackendHealthObservation.OperationSucceeded>(reporter.reports.last().second)

        val degraded = BackendServiceHealth(
            id = BackendServiceIds.FIREBASE_FIRESTORE,
            name = "Cloud Firestore",
            status = BackendHealthStatus.WORKING,
            detail = "Bounded server read completed",
        ).withObservation(reporter.reports.first().second)
        val recovered = degraded.withObservation(reporter.reports.last().second)
        assertEquals(BackendHealthStatus.DEGRADED, degraded.status)
        assertEquals(BackendHealthStatus.WORKING, recovered.status)
        assertEquals("Cloud Firestore mutation completed", recovered.detail)
    }

    // --- deleteActivity ---

    @Test
    fun `GIVEN an existing document WHEN deleting it THEN it is deleted and returned`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        val organizerRef = mockk<DocumentReference>()
        val participantRef = mockk<DocumentReference>()
        every { activitiesCollection.document(ACTIVITY_ID.value) } returns docRef
        every { usersCollection.document(ORGANIZER_ID.value) } returns organizerRef
        every { usersCollection.document(PARTICIPANT_ID.value) } returns participantRef
        val snapshot = mockk<DocumentSnapshot> {
            every { toObject(IturActivityDTO::class.java) } returns ACTIVITY_DTO
        }
        every { transaction.get(docRef) } returns snapshot
        every { transaction.get(organizerRef) } returns reservationSnapshot(ACTIVITY_ID.value)
        every { transaction.get(participantRef) } returns reservationSnapshot(ACTIVITY_ID.value)

        val result = repository.deleteActivity(ACTIVITY_ID)

        assertIs<DataResult.Success<IturActivity>>(result)
        verify(exactly = 1) { transaction.delete(docRef) }
    }

    @Test
    fun `GIVEN no document WHEN deleting THEN returns NotFound without calling delete`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        every { activitiesCollection.document(ACTIVITY_ID.value) } returns docRef
        val snapshot = mockk<DocumentSnapshot> {
            every { toObject(IturActivityDTO::class.java) } returns null
        }
        every { transaction.get(docRef) } returns snapshot

        val result = repository.deleteActivity(ACTIVITY_ID)

        assertIs<DataResult.NotFound>(result)
        verify(exactly = 0) { transaction.delete(docRef) }
    }

    @Test
    fun `GIVEN Firestore throws WHEN deleting an activity THEN returns Error rather than propagating`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        every { activitiesCollection.document(ACTIVITY_ID.value) } returns docRef
        every { transaction.get(docRef) } throws RuntimeException("offline")

        val result = repository.deleteActivity(ACTIVITY_ID)

        assertIs<DataResult.Error>(result)
        Unit
    }

    // --- addParticipant / removeParticipant ---

    @Test
    fun `WHEN adding a participant THEN trusted admission joins and the stored activity is returned`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        every { activitiesCollection.document(ACTIVITY_ID.value) } returns docRef
        every { docRef.get() } returns successfulTask(
            mockk {
                every { exists() } returns true
                every { toObject(IturActivityDTO::class.java) } returns ACTIVITY_DTO
            },
        )
        admissionGateway.joinResult = DataResult.Success(ACTIVITY_ID)

        val result = repository.addParticipant(ACTIVITY_ID, PARTICIPANT_ID)

        assertIs<DataResult.Success<IturActivity>>(result)
        assertEquals(listOf(ACTIVITY_ID), admissionGateway.joinCalls)
    }

    @Test
    fun `GIVEN trusted join rejects capacity WHEN joining THEN the neutral code is preserved`() = runBlocking {
        admissionGateway.joinResult = DataResult.Error(
            "This activity is full.",
            DataErrorCode.ACTIVITY_FULL,
        )

        val result = repository.addParticipant(ACTIVITY_ID, PARTICIPANT_ID)

        val error = assertIs<DataResult.Error>(result)
        assertEquals(DataErrorCode.ACTIVITY_FULL, error.code)
        assertEquals(listOf(ACTIVITY_ID), admissionGateway.joinCalls)
    }

    @Test
    fun `WHEN removing a participant THEN trusted departure runs and the stored activity is returned`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        every { activitiesCollection.document(ACTIVITY_ID.value) } returns docRef
        every { docRef.get() } returns successfulTask(mockk {
            every { exists() } returns true
            every { toObject(IturActivityDTO::class.java) } returns ACTIVITY_DTO
        })
        admissionGateway.leaveResult = DataResult.Success(ACTIVITY_ID)

        val result = repository.removeParticipant(ACTIVITY_ID, PARTICIPANT_ID)

        assertIs<DataResult.Success<IturActivity>>(result)
        assertEquals(listOf(ACTIVITY_ID), admissionGateway.leaveCalls)
        verify(exactly = 0) { transaction.update(any<DocumentReference>(), any<FieldPath>(), any(), *anyVararg()) }
    }

    @Test
    fun `GIVEN refresh fails after trusted join THEN adding a participant returns Error`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        every { activitiesCollection.document(ACTIVITY_ID.value) } returns docRef
        every { docRef.get() } returns failedTask(RuntimeException("denied"))
        admissionGateway.joinResult = DataResult.Success(ACTIVITY_ID)

        val result = repository.addParticipant(ACTIVITY_ID, PARTICIPANT_ID)

        assertIs<DataResult.Error>(result)
        Unit
    }

    // --- participant signals ---

    @Test
    fun `GIVEN a current participant WHEN setting a signal THEN only their nested field is replaced`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        every { activitiesCollection.document(ACTIVITY_ID.value) } returns docRef
        val before = mockk<DocumentSnapshot> {
            every { toObject(IturActivityDTO::class.java) } returns ACTIVITY_DTO
        }
        val afterDto = ACTIVITY_DTO.copy(
            participantSignals = mapOf(PARTICIPANT_ID.value to ParticipantSignal.DELAYED.name),
        )
        val after = mockk<DocumentSnapshot> {
            every { toObject(IturActivityDTO::class.java) } returns afterDto
        }
        every { docRef.get() } returns successfulTask(before) andThen successfulTask(after)
        val fieldPath = slot<FieldPath>()
        every {
            docRef.update(capture(fieldPath), any(), any(), any())
        } returns successfulTask(null)

        val result = repository.participantSignalRepository.setParticipantSignal(
            ACTIVITY_ID,
            PARTICIPANT_ID,
            ParticipantSignal.DELAYED,
        )

        assertIs<DataResult.Success<IturActivity>>(result)
        assertEquals(ParticipantSignal.DELAYED, result.data.participantSignals[PARTICIPANT_ID])
        assertEquals("participantSignals.${PARTICIPANT_ID.value}", fieldPath.captured.toString())
        verify(exactly = 1) {
            docRef.update(
                any<FieldPath>(),
                ParticipantSignal.DELAYED.name,
                "attentionRequests",
                match { it.javaClass.simpleName == "ArrayRemoveFieldValue" },
            )
        }
    }

    @Test
    fun `GIVEN the same explicit signal WHEN setting it again THEN no write occurs`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        every { activitiesCollection.document(ACTIVITY_ID.value) } returns docRef
        val snapshot = mockk<DocumentSnapshot> {
            every { toObject(IturActivityDTO::class.java) } returns ACTIVITY_DTO.copy(
                participantSignals = mapOf(PARTICIPANT_ID.value to ParticipantSignal.DELAYED.name),
            )
        }
        every { docRef.get() } returns successfulTask(snapshot)

        val result = repository.participantSignalRepository.setParticipantSignal(
            ACTIVITY_ID,
            PARTICIPANT_ID,
            ParticipantSignal.DELAYED,
        )

        assertIs<DataResult.Success<IturActivity>>(result)
        verify(exactly = 0) { docRef.update(any<FieldPath>(), any(), *anyVararg()) }
    }

    @Test
    fun `GIVEN organiser nonmember or terminal activity WHEN setting a signal THEN each is rejected without a write`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        every { activitiesCollection.document(ACTIVITY_ID.value) } returns docRef
        val organizerSnapshot = mockk<DocumentSnapshot> {
            every { toObject(IturActivityDTO::class.java) } returns ACTIVITY_DTO
        }
        val terminalSnapshot = mockk<DocumentSnapshot> {
            every { toObject(IturActivityDTO::class.java) } returns ACTIVITY_DTO.copy(status = "FINISHED")
        }
        every { docRef.get() } returns
            successfulTask(organizerSnapshot) andThen
            successfulTask(organizerSnapshot) andThen
            successfulTask(terminalSnapshot)

        assertIs<DataResult.Error>(
            repository.participantSignalRepository.setParticipantSignal(
                ACTIVITY_ID,
                ORGANIZER_ID,
                ParticipantSignal.NEEDS_HELP,
            ),
        )
        assertIs<DataResult.Error>(
            repository.participantSignalRepository.setParticipantSignal(
                ACTIVITY_ID,
                UserId("outsider"),
                ParticipantSignal.DELAYED,
            ),
        )
        assertIs<DataResult.Error>(
            repository.participantSignalRepository.setParticipantSignal(
                ACTIVITY_ID,
                PARTICIPANT_ID,
                ParticipantSignal.DELAYED,
            ),
        )
        verify(exactly = 0) { docRef.update(any<FieldPath>(), any(), *anyVararg()) }
    }

    @Test
    fun `GIVEN a legacy request WHEN clearing THEN nested and legacy fields are both removed`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        every { activitiesCollection.document(ACTIVITY_ID.value) } returns docRef
        val before = mockk<DocumentSnapshot> {
            every { toObject(IturActivityDTO::class.java) } returns ACTIVITY_DTO.copy(
                attentionRequests = listOf(PARTICIPANT_ID.value),
            )
        }
        val after = mockk<DocumentSnapshot> {
            every { toObject(IturActivityDTO::class.java) } returns ACTIVITY_DTO
        }
        every { docRef.get() } returns successfulTask(before) andThen successfulTask(after)
        every { docRef.update(any<FieldPath>(), any(), any(), any()) } returns successfulTask(null)

        val result = repository.participantSignalRepository.clearParticipantSignal(
            ACTIVITY_ID,
            PARTICIPANT_ID,
        )

        assertIs<DataResult.Success<IturActivity>>(result)
        assertTrue(result.data.participantSignals.isEmpty())
        verify(exactly = 1) {
            docRef.update(
                any<FieldPath>(),
                match { it.javaClass.simpleName == "DeleteFieldValue" },
                "attentionRequests",
                match { it.javaClass.simpleName == "ArrayRemoveFieldValue" },
            )
        }
    }

    @Test
    fun `GIVEN legacy and explicit states WHEN reading THEN explicit wins and legacy remains compatible`() = runBlocking {
        val otherParticipant = UserId("participant2")
        val docRef = mockk<DocumentReference>()
        every { activitiesCollection.document(ACTIVITY_ID.value) } returns docRef
        val snapshot = mockk<DocumentSnapshot> {
            every { exists() } returns true
            every { toObject(IturActivityDTO::class.java) } returns ACTIVITY_DTO.copy(
                participantIds = ACTIVITY_DTO.participantIds + otherParticipant.value,
                participantSignals = mapOf(
                    PARTICIPANT_ID.value to ParticipantSignal.DELAYED.name,
                    "unknown-user" to "FUTURE_STATE",
                    "former-participant" to ParticipantSignal.NEEDS_HELP.name,
                ),
                attentionRequests = listOf(PARTICIPANT_ID.value, otherParticipant.value),
            )
        }
        every { docRef.get() } returns successfulTask(snapshot)

        val result = repository.getActivity(ACTIVITY_ID)

        assertIs<DataResult.Success<IturActivity>>(result)
        assertEquals(ParticipantSignal.DELAYED, result.data.participantSignals[PARTICIPANT_ID])
        assertEquals(ParticipantSignal.NEEDS_HELP, result.data.participantSignals[otherParticipant])
        assertTrue(UserId("unknown-user") !in result.data.participantSignals)
        assertTrue(UserId("former-participant") !in result.data.participantSignals)
    }

    @Test
    fun `GIVEN Firestore write fails WHEN setting a signal THEN returns Error`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        every { activitiesCollection.document(ACTIVITY_ID.value) } returns docRef
        val snapshot = mockk<DocumentSnapshot> {
            every { toObject(IturActivityDTO::class.java) } returns ACTIVITY_DTO
        }
        every { docRef.get() } returns successfulTask(snapshot)
        every { docRef.update(any<FieldPath>(), any(), any(), any()) } returns
            failedTask(RuntimeException("denied"))

        val result = repository.participantSignalRepository.setParticipantSignal(
            ACTIVITY_ID,
            PARTICIPANT_ID,
            ParticipantSignal.NEEDS_HELP,
        )

        assertIs<DataResult.Error>(result)
        Unit
    }

    // --- getBroadcastsSince ---

    @Test
    fun `GIVEN since is null WHEN getting broadcasts THEN queries without a lower bound`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        every { activitiesCollection.document(ACTIVITY_ID.value) } returns docRef
        val broadcasts = mockk<CollectionReference>()
        every { docRef.collection("broadcasts") } returns broadcasts
        val ordered = mockk<Query>()
        every { broadcasts.orderBy("sentOn") } returns ordered
        val querySnapshot = mockk<QuerySnapshot> {
            every { toObjects(BroadcastDTO::class.java) } returns listOf(BroadcastDTO(id = "b1", message = "hi", sentOn = Date()))
        }
        every { ordered.get() } returns successfulTask(querySnapshot)

        val result = repository.getBroadcastsSince(ACTIVITY_ID, since = null)

        assertEquals(1, result.size)
        verify(exactly = 0) { ordered.whereGreaterThan(any<String>(), any()) }
    }

    @Test
    fun `GIVEN since is set WHEN getting broadcasts THEN queries with a lower bound`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        every { activitiesCollection.document(ACTIVITY_ID.value) } returns docRef
        val broadcasts = mockk<CollectionReference>()
        every { docRef.collection("broadcasts") } returns broadcasts
        val ordered = mockk<Query>()
        every { broadcasts.orderBy("sentOn") } returns ordered
        val bounded = mockk<Query>()
        every { ordered.whereGreaterThan("sentOn", any<Timestamp>()) } returns bounded
        val querySnapshot = mockk<QuerySnapshot> { every { toObjects(BroadcastDTO::class.java) } returns emptyList() }
        every { bounded.get() } returns successfulTask(querySnapshot)

        repository.getBroadcastsSince(ACTIVITY_ID, since = Date())

        verify(exactly = 1) { ordered.whereGreaterThan("sentOn", any<Timestamp>()) }
    }

    @Test
    fun `GIVEN a broadcast document missing required fields WHEN getting broadcasts THEN it is skipped`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        every { activitiesCollection.document(ACTIVITY_ID.value) } returns docRef
        val broadcasts = mockk<CollectionReference>()
        every { docRef.collection("broadcasts") } returns broadcasts
        val ordered = mockk<Query>()
        every { broadcasts.orderBy("sentOn") } returns ordered
        val incomplete = BroadcastDTO(id = "b1", message = null, sentOn = Date())
        val querySnapshot = mockk<QuerySnapshot> { every { toObjects(BroadcastDTO::class.java) } returns listOf(incomplete) }
        every { ordered.get() } returns successfulTask(querySnapshot)

        val result = repository.getBroadcastsSince(ACTIVITY_ID, since = null)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `GIVEN Firestore throws WHEN getting broadcasts THEN propagates for availability reporting`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        every { activitiesCollection.document(ACTIVITY_ID.value) } returns docRef
        val broadcasts = mockk<CollectionReference>()
        every { docRef.collection("broadcasts") } returns broadcasts
        val ordered = mockk<Query>()
        every { broadcasts.orderBy("sentOn") } returns ordered
        every { ordered.get() } returns failedTask(RuntimeException("offline"))

        val exception = assertFailsWith<RuntimeException> {
            repository.getBroadcastsSince(ACTIVITY_ID, since = null)
        }
        assertEquals("offline", exception.message)
    }
}
