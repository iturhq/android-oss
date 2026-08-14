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
import com.google.firebase.firestore.FieldValue
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
    private val repository = FirebaseActivityRepository(
        firestore,
        mockk<BackendHealthReporter>(relaxed = true),
        transactionExecutor,
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
    fun `GIVEN a ready activity WHEN starting THEN every canonical member is reserved`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        val organizerRef = mockk<DocumentReference>()
        val participantRef = mockk<DocumentReference>()
        every { activitiesCollection.document(ACTIVITY_ID.value) } returns docRef
        every { usersCollection.document(ORGANIZER_ID.value) } returns organizerRef
        every { usersCollection.document(PARTICIPANT_ID.value) } returns participantRef
        every { transaction.get(docRef) } returns mockk {
            every { toObject(IturActivityDTO::class.java) } returns ACTIVITY_DTO.copy(status = "READY")
        }
        every { transaction.get(organizerRef) } returns reservationSnapshot(null)

        val result = repository.updateActivityStatus(ACTIVITY_ID, IturActivityStatus.ONGOING)

        assertIs<DataResult.Success<IturActivity>>(result)
        verify(exactly = 0) { transaction.get(participantRef) }
        listOf(organizerRef, participantRef).forEach { memberRef ->
            verify(exactly = 1) {
                transaction.set(
                    memberRef,
                    match<Map<String, String>> { it["activeActivityId"] == ACTIVITY_ID.value },
                    any<SetOptions>(),
                )
            }
        }
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
    fun `WHEN creating an activity THEN it is stored as ONGOING with the organizer as sole participant`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        val userRef = mockk<DocumentReference>()
        every { activitiesCollection.document() } returns docRef
        every { usersCollection.document(ORGANIZER_ID.value) } returns userRef
        every { docRef.id } returns "NewActivity000000001"
        val dto = slot<IturActivityDTO>()
        every { transaction.get(userRef) } returns reservationSnapshot(null)
        every { transaction.set(docRef, capture(dto)) } returns transaction

        val result = repository.createActivity(ORGANIZER_ID)

        assertIs<DataResult.Success<IturActivity>>(result)
        assertEquals("NewActivity000000001", result.data.id.value)
        assertEquals(ORGANIZER_ID, result.data.organizerId)
        assertEquals(listOf(ORGANIZER_ID), result.data.participantIds)
        assertEquals(IturActivityStatus.ONGOING, result.data.status)
        assertEquals("ONGOING", dto.captured.status)
        verify(exactly = 1) {
            transaction.set(
                userRef,
                match<Map<String, String>> { it["activeActivityId"] == "NewActivity000000001" },
                any<SetOptions>(),
            )
        }
    }

    @Test
    fun `GIVEN Firestore throws WHEN creating an activity THEN the exception propagates`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        val userRef = mockk<DocumentReference>()
        every { activitiesCollection.document() } returns docRef
        every { usersCollection.document(ORGANIZER_ID.value) } returns userRef
        every { docRef.id } returns "NewActivity000000001"
        every { transaction.get(userRef) } throws RuntimeException("no network")

        assertFailsWith<RuntimeException> { repository.createActivity(ORGANIZER_ID) }
        Unit
    }

    @Test
    fun `permission denied mutation reports generic degradation and next mutation recovers`() = runBlocking {
        val reporter = RecordingBackendHealthReporter()
        val healthAwareRepository = FirebaseActivityRepository(firestore, reporter, transactionExecutor)
        val docRef = mockk<DocumentReference>()
        val userRef = mockk<DocumentReference>()
        val privateFailure = FirebaseFirestoreException(
            "PERMISSION_DENIED activity=${ACTIVITY_ID.value} user=${PARTICIPANT_ID.value} token=secret",
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
        )
        every { activitiesCollection.document() } returns docRef
        every { usersCollection.document(ORGANIZER_ID.value) } returns userRef
        every { docRef.id } returns "NewActivity000000001"
        every { transaction.get(userRef) } throws privateFailure andThen reservationSnapshot(null)

        assertFailsWith<FirebaseFirestoreException> {
            healthAwareRepository.createActivity(ORGANIZER_ID)
        }
        assertIs<DataResult.Success<IturActivity>>(healthAwareRepository.createActivity(ORGANIZER_ID))

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
    fun `WHEN adding a participant THEN participantIds is updated with an arrayUnion`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        val userRef = mockk<DocumentReference>()
        every { activitiesCollection.document(ACTIVITY_ID.value) } returns docRef
        every { usersCollection.document(PARTICIPANT_ID.value) } returns userRef
        val fieldValue = slot<FieldValue>()
        every { transaction.update(docRef, "participantIds", capture(fieldValue)) } returns transaction
        val activitySnapshot = mockk<DocumentSnapshot> {
            every { toObject(IturActivityDTO::class.java) } returns ACTIVITY_DTO
        }
        every { transaction.get(docRef) } returns activitySnapshot
        every { transaction.get(userRef) } returns reservationSnapshot(null)

        val result = repository.addParticipant(ACTIVITY_ID, PARTICIPANT_ID)

        assertIs<DataResult.Success<IturActivity>>(result)
        assertEquals("ArrayUnionFieldValue", fieldValue.captured.javaClass.simpleName)
        verify(exactly = 1) {
            transaction.set(
                userRef,
                match<Map<String, String>> { it["activeActivityId"] == ACTIVITY_ID.value },
                any<SetOptions>(),
            )
        }
    }

    @Test
    fun `GIVEN another live reservation WHEN joining THEN transaction rejects without a write`() = runBlocking {
        val targetRef = mockk<DocumentReference>()
        val userRef = mockk<DocumentReference>()
        val otherId = IturActivityId("OtherActivity0000001")
        val otherRef = mockk<DocumentReference>()
        every { activitiesCollection.document(ACTIVITY_ID.value) } returns targetRef
        every { activitiesCollection.document(otherId.value) } returns otherRef
        every { usersCollection.document(PARTICIPANT_ID.value) } returns userRef
        every { transaction.get(targetRef) } returns mockk {
            every { toObject(IturActivityDTO::class.java) } returns ACTIVITY_DTO
        }
        every { transaction.get(userRef) } returns reservationSnapshot(otherId.value)
        every { transaction.get(otherRef) } returns mockk {
            every { toObject(IturActivityDTO::class.java) } returns ACTIVITY_DTO.copy(
                id = otherId.value,
                participantIds = listOf(PARTICIPANT_ID.value),
            )
        }

        val result = repository.addParticipant(ACTIVITY_ID, PARTICIPANT_ID)

        assertIs<DataResult.Error>(result)
        assertTrue(result.message.contains("already active"))
        verify(exactly = 0) { transaction.update(targetRef, any<String>(), any()) }
    }

    @Test
    fun `WHEN removing a participant THEN participantIds is updated with an arrayRemove`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        val userRef = mockk<DocumentReference>()
        every { activitiesCollection.document(ACTIVITY_ID.value) } returns docRef
        every { usersCollection.document(PARTICIPANT_ID.value) } returns userRef
        val fieldPath = slot<FieldPath>()
        every {
            transaction.update(docRef, capture(fieldPath), any(), any(), any(), any(), any())
        } returns transaction
        val activitySnapshot = mockk<DocumentSnapshot> {
            every { toObject(IturActivityDTO::class.java) } returns ACTIVITY_DTO
        }
        every { transaction.get(docRef) } returns activitySnapshot
        every { transaction.get(userRef) } returns reservationSnapshot(ACTIVITY_ID.value)

        val result = repository.removeParticipant(ACTIVITY_ID, PARTICIPANT_ID)

        assertIs<DataResult.Success<IturActivity>>(result)
        assertEquals("participantSignals.${PARTICIPANT_ID.value}", fieldPath.captured.toString())
        verify(exactly = 1) {
            transaction.update(
                docRef,
                any<FieldPath>(),
                match { it.javaClass.simpleName == "DeleteFieldValue" },
                "participantIds",
                match { it.javaClass.simpleName == "ArrayRemoveFieldValue" },
                "attentionRequests",
                match { it.javaClass.simpleName == "ArrayRemoveFieldValue" },
            )
        }
        verify(exactly = 1) {
            transaction.set(
                userRef,
                match<Map<String, String?>> { it["activeActivityId"] == null },
                any<SetOptions>(),
            )
        }
    }

    @Test
    fun `GIVEN Firestore throws WHEN updating participants THEN returns Error rather than propagating`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        every { activitiesCollection.document(ACTIVITY_ID.value) } returns docRef
        every { transaction.get(docRef) } throws RuntimeException("denied")

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
