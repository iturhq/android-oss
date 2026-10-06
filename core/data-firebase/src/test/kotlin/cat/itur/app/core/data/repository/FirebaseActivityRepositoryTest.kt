/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.core.data.repository

import cat.itur.app.core.data.health.BackendHealthObservation
import cat.itur.app.core.data.health.BackendHealthReporter
import cat.itur.app.core.data.health.BackendHealthStatus
import cat.itur.app.core.data.health.BackendServiceHealth
import cat.itur.app.core.data.health.BackendServiceIds
import cat.itur.app.core.data.health.withObservation
import cat.itur.app.core.domain.id.IturActivityId
import cat.itur.app.core.domain.id.UserId
import cat.itur.app.core.model.IturActivity
import cat.itur.app.core.model.IturActivityStatus
import cat.itur.app.core.model.ParticipantSignal
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Transaction
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.HttpsCallableReference
import com.google.firebase.functions.HttpsCallableResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
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

private fun activityDocument(
    documentId: String = ACTIVITY_ID.value,
    dto: IturActivityDTO = ACTIVITY_DTO,
): DocumentSnapshot = mockk {
    every { id } returns documentId
    every { toObject(IturActivityDTO::class.java) } returns dto
}

private fun activityQuery(vararg snapshots: DocumentSnapshot): QuerySnapshot = mockk {
    every { documents } returns snapshots.toList()
}

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
    var signalResult: DataResult<IturActivityId> = DataResult.Error("signal not configured")
    val startCalls = mutableListOf<IturActivityId?>()
    val joinCalls = mutableListOf<IturActivityId>()
    val leaveCalls = mutableListOf<IturActivityId>()
    val signalCalls = mutableListOf<Pair<IturActivityId, ParticipantSignal?>>()

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

    override suspend fun setParticipantSignal(
        activityId: IturActivityId,
        signal: ParticipantSignal?,
    ): DataResult<IturActivityId> {
        signalCalls += activityId to signal
        return signalResult
    }
}

private fun reservationSnapshot(activeActivityId: String?): DocumentSnapshot = mockk {
    every { getString("activeActivityId") } returns activeActivityId
}

@Suppress("LargeClass")
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

    @Test
    fun `GIVEN the production Firestore adapters WHEN changing status THEN their transaction result is mapped`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        every { activitiesCollection.document(ACTIVITY_ID.value) } returns docRef
        every { firestore.runTransaction<IturActivityDTO>(any()) } returns successfulTask(ACTIVITY_DTO)
        val productionAdapterRepository =
            FirebaseActivityRepository(firestore, mockk<FirebaseFunctions>(relaxed = true))

        val result = productionAdapterRepository.updateActivityStatus(ACTIVITY_ID, IturActivityStatus.READY)

        assertEquals(ACTIVITY_ID, assertIs<DataResult.Success<IturActivity>>(result).data.id)
        verify(exactly = 1) { firestore.runTransaction<IturActivityDTO>(any()) }
    }

    @Test
    fun `GIVEN callable-joined member signal WHEN loading backend-shaped state THEN it is applied`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        val signals = mockk<CollectionReference>()
        val participants = mockk<CollectionReference>()
        val canonicalSignal = mockk<DocumentSnapshot> {
            every { id } returns PARTICIPANT_ID.value
            every { getString("signal") } returns ParticipantSignal.DELAYED.name
        }
        val callableJoinedMember = mockk<DocumentSnapshot> {
            every { id } returns PARTICIPANT_ID.value
        }
        every { activitiesCollection.document(ACTIVITY_ID.value) } returns docRef
        every { docRef.get() } returns successfulTask(
            mockk {
                every { exists() } returns true
                every { toObject(IturActivityDTO::class.java) } returns ACTIVITY_DTO.copy(
                    participantIds = listOf(ORGANIZER_ID.value),
                )
            },
        )
        every { docRef.collection("participantSignals") } returns signals
        every { docRef.collection("participants") } returns participants
        every { signals.get() } returns successfulTask(
            mockk<QuerySnapshot> { every { documents } returns listOf(canonicalSignal) },
        )
        every { participants.get() } returns successfulTask(
            mockk<QuerySnapshot> { every { documents } returns listOf(callableJoinedMember) },
        )
        val productionAdapterRepository =
            FirebaseActivityRepository(firestore, mockk<FirebaseFunctions>(relaxed = true))

        val result = productionAdapterRepository.getActivity(ACTIVITY_ID)

        assertEquals(
            ParticipantSignal.DELAYED,
            assertIs<DataResult.Success<IturActivity>>(result).data.participantSignals[PARTICIPANT_ID],
        )
    }

    @Test
    fun `GIVEN legacy convenience constructors WHEN changing status THEN they retain the production transaction wiring`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        every { activitiesCollection.document(ACTIVITY_ID.value) } returns docRef
        every { firestore.runTransaction<IturActivityDTO>(any()) } returns successfulTask(ACTIVITY_DTO)
        mockkObject(FirebaseFunctions.Companion)
        try {
            every { FirebaseFunctions.getInstance() } returns mockk(relaxed = true)

            assertIs<DataResult.Success<IturActivity>>(
                FirebaseActivityRepository(firestore).updateActivityStatus(ACTIVITY_ID, IturActivityStatus.READY),
            )
            assertIs<DataResult.Success<IturActivity>>(
                FirebaseActivityRepository(firestore, RecordingBackendHealthReporter())
                    .updateActivityStatus(ACTIVITY_ID, IturActivityStatus.READY),
            )
        } finally {
            unmockkObject(FirebaseFunctions.Companion)
        }
        verify(exactly = 2) { firestore.runTransaction<IturActivityDTO>(any()) }
    }

    @Test
    fun `GIVEN trusted start reports a missing activity WHEN updating to ongoing THEN the missing result is preserved`() = runBlocking {
        admissionGateway.startResult = DataResult.NotFound(ACTIVITY_ID.value)

        val result = repository.updateActivityStatus(ACTIVITY_ID, IturActivityStatus.ONGOING)

        assertEquals(ACTIVITY_ID.value, assertIs<DataResult.NotFound>(result).id)
    }

    @Test
    fun `GIVEN trusted creation reports a missing activity WHEN creating THEN the missing result is preserved`() = runBlocking {
        admissionGateway.startResult = DataResult.NotFound(ACTIVITY_ID.value)

        val result = repository.createActivity(ORGANIZER_ID)

        assertEquals(ACTIVITY_ID.value, assertIs<DataResult.NotFound>(result).id)
    }

    @Test
    fun `GIVEN trusted departure fails or misses its activity WHEN removing THEN its result is preserved`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        every { activitiesCollection.document(ACTIVITY_ID.value) } returns docRef
        every { docRef.get() } returns successfulTask(
            mockk {
                every { exists() } returns true
                every { toObject(IturActivityDTO::class.java) } returns ACTIVITY_DTO
            },
        )
        admissionGateway.leaveResult = DataResult.Error("departure rejected")

        assertEquals(
            "departure rejected",
            assertIs<DataResult.Error>(repository.removeParticipant(ACTIVITY_ID, PARTICIPANT_ID)).message,
        )

        admissionGateway.leaveResult = DataResult.NotFound(ACTIVITY_ID.value)

        assertEquals(
            ACTIVITY_ID.value,
            assertIs<DataResult.NotFound>(repository.removeParticipant(ACTIVITY_ID, PARTICIPANT_ID)).id,
        )
    }

    @Test
    fun `GIVEN attention admission succeeds WHEN requesting attention THEN it completes`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        every { activitiesCollection.document(ACTIVITY_ID.value) } returns docRef
        every { docRef.get() } returns successfulTask(
            mockk {
                every { exists() } returns true
                every { toObject(IturActivityDTO::class.java) } returns ACTIVITY_DTO
            },
        )
        admissionGateway.signalResult = DataResult.Success(ACTIVITY_ID)

        repository.requestAttention(ACTIVITY_ID, PARTICIPANT_ID)

        assertEquals(
            listOf<Pair<IturActivityId, ParticipantSignal?>>(ACTIVITY_ID to ParticipantSignal.NEEDS_HELP),
            admissionGateway.signalCalls,
        )
    }

    @Test
    fun `GIVEN invalid stored legacy signals WHEN hydrating THEN they are ignored`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        every { activitiesCollection.document(ACTIVITY_ID.value) } returns docRef
        every { docRef.get() } returns successfulTask(
            mockk {
                every { exists() } returns true
                every { toObject(IturActivityDTO::class.java) } returns ACTIVITY_DTO.copy(
                    participantSignals = mapOf(
                        PARTICIPANT_ID.value to "OUTDATED",
                        ORGANIZER_ID.value to ParticipantSignal.DELAYED.name,
                    ),
                )
            },
        )

        val result = repository.getActivity(ACTIVITY_ID)

        assertTrue(assertIs<DataResult.Success<IturActivity>>(result).data.participantSignals.isEmpty())
    }

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
    fun `GIVEN a legacy payload ID WHEN getting its document THEN it remains readable`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        every { activitiesCollection.document(ACTIVITY_ID.value) } returns docRef
        val snapshot = activityDocument()
        every { snapshot.exists() } returns true
        every { docRef.get() } returns successfulTask(snapshot)

        val result = repository.getActivity(ACTIVITY_ID)

        assertIs<DataResult.Success<IturActivity>>(result)
        assertEquals(ACTIVITY_ID, result.data.id)
        assertEquals(ORGANIZER_ID, result.data.organizerId)
    }

    @Test
    fun `GIVEN no payload ID WHEN getting its document THEN the document ID is restored`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        every { activitiesCollection.document(ACTIVITY_ID.value) } returns docRef
        val snapshot = activityDocument(dto = ACTIVITY_DTO.copy(id = ""))
        every { snapshot.exists() } returns true
        every { docRef.get() } returns successfulTask(snapshot)

        val result = repository.getActivity(ACTIVITY_ID)

        assertEquals(ACTIVITY_ID, assertIs<DataResult.Success<IturActivity>>(result).data.id)
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

    @Test
    fun `GIVEN canonical signal loading fails WHEN getting an activity THEN the public error preserves the cause`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        every { activitiesCollection.document(ACTIVITY_ID.value) } returns docRef
        every { docRef.get() } returns successfulTask(
            mockk<DocumentSnapshot> {
                every { exists() } returns true
                every { toObject(IturActivityDTO::class.java) } returns ACTIVITY_DTO
            },
        )
        val signalLoadingRepository = FirebaseActivityRepository(
            firestore,
            mockk(relaxed = true),
            transactionExecutor,
            admissionGateway,
        ) { throw IllegalStateException("canonical signals unavailable") }

        val result = signalLoadingRepository.getActivity(ACTIVITY_ID)

        assertEquals("canonical signals unavailable", assertIs<DataResult.Error>(result).message)
    }

    @Test
    fun `GIVEN an activity with an unknown status WHEN getting it THEN conversion failure becomes Error`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        every { activitiesCollection.document(ACTIVITY_ID.value) } returns docRef
        every { docRef.get() } returns successfulTask(
            mockk<DocumentSnapshot> {
                every { exists() } returns true
                every { toObject(IturActivityDTO::class.java) } returns ACTIVITY_DTO.copy(status = "RETIRED")
            },
        )

        val result = repository.getActivity(ACTIVITY_ID)

        assertTrue(assertIs<DataResult.Error>(result).message.contains("RETIRED"))
    }

    // --- getActivities ---

    @Test
    fun `GIVEN ByOrganizer filter WHEN getting activities THEN queries only by organizerId`() = runBlocking {
        val query = mockk<Query>()
        every { activitiesCollection.whereEqualTo("organizerId", ORGANIZER_ID.value) } returns query
        val querySnapshot = activityQuery(activityDocument())
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
        val querySnapshot = activityQuery(activityDocument())
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
        val querySnapshot = activityQuery(activityDocument())
        every { ongoingQuery.get() } returns successfulTask(querySnapshot)

        val result = repository.getActivities(
            ActivityFilter.OngoingByParticipant(PARTICIPANT_ID),
        )

        assertIs<DataResult.Success<List<IturActivity>>>(result)
        assertEquals(1, result.data.size)
    }

    @Test
    fun `GIVEN a malformed document ID WHEN getting activities THEN mapping is rejected`() = runBlocking {
        val query = mockk<Query>()
        every { activitiesCollection.whereEqualTo("organizerId", ORGANIZER_ID.value) } returns query
        val querySnapshot = activityQuery(activityDocument(documentId = "malformed-id"))
        every { query.get() } returns successfulTask(querySnapshot)

        val result = repository.getActivities(ActivityFilter.ByOrganizer(ORGANIZER_ID))

        assertTrue(assertIs<DataResult.Error>(result).message.contains("Invalid activity identifier"))
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

    @Test
    fun `GIVEN a listed activity with an unknown status WHEN getting activities THEN conversion failure becomes Error`() = runBlocking {
        val query = mockk<Query>()
        every { activitiesCollection.whereEqualTo("organizerId", ORGANIZER_ID.value) } returns query
        val querySnapshot = activityQuery(activityDocument(dto = ACTIVITY_DTO.copy(status = "RETIRED")))
        every { query.get() } returns successfulTask(querySnapshot)

        val result = repository.getActivities(ActivityFilter.ByOrganizer(ORGANIZER_ID))

        assertTrue(assertIs<DataResult.Error>(result).message.contains("RETIRED"))
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
            mockk<DocumentSnapshot> {
                every { exists() } returns true
                every { toObject(IturActivityDTO::class.java) } returns ACTIVITY_DTO.copy(id = "")
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
                every { toObject(IturActivityDTO::class.java) } returns ACTIVITY_DTO.copy(id = "")
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

    @Test
    fun `GIVEN a deleted activity with an unknown status WHEN returning it THEN conversion failure becomes Error`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        every { activitiesCollection.document(ACTIVITY_ID.value) } returns docRef
        every { transaction.get(docRef) } returns mockk {
            every { toObject(IturActivityDTO::class.java) } returns ACTIVITY_DTO.copy(status = "RETIRED")
        }

        val result = repository.deleteActivity(ACTIVITY_ID)

        assertTrue(assertIs<DataResult.Error>(result).message.contains("RETIRED"))
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
    fun `GIVEN trusted admission reports a missing activity WHEN joining THEN the conflict is forwarded without a Firestore write`() = runBlocking {
        admissionGateway.joinResult = DataResult.NotFound(ACTIVITY_ID.value)

        val result = repository.addParticipant(ACTIVITY_ID, PARTICIPANT_ID)

        assertEquals(ACTIVITY_ID.value, assertIs<DataResult.NotFound>(result).id)
        assertEquals(listOf(ACTIVITY_ID), admissionGateway.joinCalls)
        verify(exactly = 0) { transaction.update(any<DocumentReference>(), any<FieldPath>(), any(), *anyVararg()) }
    }

    @Test
    fun `WHEN removing a participant THEN trusted departure runs and the stored activity is returned`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        every { activitiesCollection.document(ACTIVITY_ID.value) } returns docRef
        every { docRef.get() } returns successfulTask(
            mockk {
                every { exists() } returns true
                every { toObject(IturActivityDTO::class.java) } returns ACTIVITY_DTO
            },
        )
        admissionGateway.leaveResult = DataResult.Success(ACTIVITY_ID)

        val result = repository.removeParticipant(ACTIVITY_ID, PARTICIPANT_ID)

        assertIs<DataResult.Success<IturActivity>>(result)
        assertEquals(listOf(ACTIVITY_ID), admissionGateway.leaveCalls)
        verify(exactly = 0) { transaction.update(any<DocumentReference>(), any<FieldPath>(), any(), *anyVararg()) }
    }

    @Test
    fun `GIVEN production departure revokes member reads WHEN removing THEN the successful leave stays successful`() = runBlocking {
        var departed = false
        val docRef = mockk<DocumentReference>()
        val signals = mockk<CollectionReference>()
        val participants = mockk<CollectionReference>()
        every { activitiesCollection.document(ACTIVITY_ID.value) } returns docRef
        every { docRef.get() } returns successfulTask(
            mockk {
                every { exists() } returns true
                every { toObject(IturActivityDTO::class.java) } returns ACTIVITY_DTO.copy(
                    participantIds = listOf(ORGANIZER_ID.value),
                )
            },
        )
        every { docRef.collection("participantSignals") } returns signals
        every { docRef.collection("participants") } returns participants
        every { signals.get() } answers {
            if (departed) failedTask(postLeavePermissionDenied()) else successfulTask(emptyQuerySnapshot())
        }
        every { participants.get() } answers {
            if (departed) failedTask(postLeavePermissionDenied()) else successfulTask(emptyQuerySnapshot())
        }
        val leaveCallable = mockk<HttpsCallableReference> {
            every { call(mapOf("activityId" to ACTIVITY_ID.value)) } answers {
                departed = true
                successfulTask(
                    HttpsCallableResult::class.java
                        .getConstructor(Any::class.java)
                        .newInstance(mapOf("activityId" to ACTIVITY_ID.value)),
                )
            }
        }
        val functions = mockk<FirebaseFunctions> {
            every { getHttpsCallable("leaveActivity") } returns leaveCallable
        }
        val productionAdapterRepository = FirebaseActivityRepository(firestore, functions)

        val result = productionAdapterRepository.removeParticipant(ACTIVITY_ID, PARTICIPANT_ID)

        assertIs<DataResult.Success<IturActivity>>(result)
        assertTrue(departed)
        verify(exactly = 0) { signals.get() }
        verify(exactly = 0) { participants.get() }
        verify(exactly = 1) { leaveCallable.call(mapOf("activityId" to ACTIVITY_ID.value)) }
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
    fun `WHEN setting a signal THEN the trusted callable owns the authenticated write`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        every { activitiesCollection.document(ACTIVITY_ID.value) } returns docRef
        every { docRef.get() } returns successfulTask(
            mockk {
                every { exists() } returns true
                every { toObject(IturActivityDTO::class.java) } returns ACTIVITY_DTO
            },
        )
        admissionGateway.signalResult = DataResult.Success(ACTIVITY_ID)

        val result = repository.participantSignalRepository.setParticipantSignal(
            ACTIVITY_ID,
            PARTICIPANT_ID,
            ParticipantSignal.DELAYED,
        )

        assertIs<DataResult.Success<IturActivity>>(result)
        assertEquals(
            listOf<Pair<IturActivityId, ParticipantSignal?>>(ACTIVITY_ID to ParticipantSignal.DELAYED),
            admissionGateway.signalCalls,
        )
    }

    @Test
    fun `GIVEN the backend rejects a signal WHEN setting it THEN no Firestore parent write is attempted`() = runBlocking {
        admissionGateway.signalResult = DataResult.Error("Only a current participant may change their signal")

        val result = repository.participantSignalRepository.setParticipantSignal(
            ACTIVITY_ID,
            PARTICIPANT_ID,
            ParticipantSignal.NEEDS_HELP,
        )

        assertIs<DataResult.Error>(result)
        assertEquals(
            listOf<Pair<IturActivityId, ParticipantSignal?>>(ACTIVITY_ID to ParticipantSignal.NEEDS_HELP),
            admissionGateway.signalCalls,
        )
        verify(exactly = 0) { transaction.update(any<DocumentReference>(), any<FieldPath>(), any(), *anyVararg()) }
    }

    @Test
    fun `WHEN clearing a participant signal THEN the trusted callable receives a null signal`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        every { activitiesCollection.document(ACTIVITY_ID.value) } returns docRef
        every { docRef.get() } returns successfulTask(
            mockk {
                every { exists() } returns true
                every { toObject(IturActivityDTO::class.java) } returns ACTIVITY_DTO
            },
        )
        admissionGateway.signalResult = DataResult.Success(ACTIVITY_ID)

        val result = repository.participantSignalRepository.clearParticipantSignal(ACTIVITY_ID, PARTICIPANT_ID)

        assertIs<DataResult.Success<IturActivity>>(result)
        assertEquals(
            listOf<Pair<IturActivityId, ParticipantSignal?>>(ACTIVITY_ID to null),
            admissionGateway.signalCalls,
        )
    }

    @Test
    fun `requesting attention exposes trusted signal failures`() = runBlocking {
        admissionGateway.signalResult = DataResult.Error("not a participant")

        val failure = assertFailsWith<IllegalStateException> {
            repository.requestAttention(ACTIVITY_ID, PARTICIPANT_ID)
        }

        assertEquals("not a participant", failure.message)
        assertEquals(
            listOf<Pair<IturActivityId, ParticipantSignal?>>(ACTIVITY_ID to ParticipantSignal.NEEDS_HELP),
            admissionGateway.signalCalls,
        )
    }

    @Test
    fun `requesting attention exposes a missing activity`() = runBlocking {
        admissionGateway.signalResult = DataResult.NotFound(ACTIVITY_ID.value)

        val failure = assertFailsWith<IllegalStateException> {
            repository.requestAttention(ACTIVITY_ID, PARTICIPANT_ID)
        }

        assertEquals("Activity ${ACTIVITY_ID.value} not found", failure.message)
    }

    @Test
    fun `GIVEN canonical and legacy signal states WHEN hydrating THEN canonical state wins and clear suppresses legacy`() = runBlocking {
        val otherParticipant = UserId("participant2")
        val docRef = mockk<DocumentReference>()
        every { activitiesCollection.document(ACTIVITY_ID.value) } returns docRef
        every { docRef.get() } returns successfulTask(
            mockk {
                every { exists() } returns true
                every { toObject(IturActivityDTO::class.java) } returns ACTIVITY_DTO.copy(
                    participantIds = ACTIVITY_DTO.participantIds + otherParticipant.value,
                    attentionRequests = listOf(PARTICIPANT_ID.value, otherParticipant.value),
                )
            },
        )
        val hydratedRepository = FirebaseActivityRepository(
            firestore,
            mockk<BackendHealthReporter>(relaxed = true),
            transactionExecutor,
            admissionGateway,
        ) {
            CanonicalParticipantState(
                signals = mapOf(
                    PARTICIPANT_ID.value to ParticipantSignal.DELAYED.name,
                    otherParticipant.value to null,
                ),
            )
        }

        val result = hydratedRepository.getActivity(ACTIVITY_ID)

        assertIs<DataResult.Success<IturActivity>>(result)
        assertEquals(ParticipantSignal.DELAYED, result.data.participantSignals[PARTICIPANT_ID])
        assertTrue(otherParticipant !in result.data.participantSignals)
    }

    @Test
    fun `GIVEN canonical signal state for a non-member WHEN hydrating THEN it is not exposed`() = runBlocking {
        val departedParticipant = UserId("departedParticipant")
        val docRef = mockk<DocumentReference>()
        every { activitiesCollection.document(ACTIVITY_ID.value) } returns docRef
        every { docRef.get() } returns successfulTask(
            mockk {
                every { exists() } returns true
                every { toObject(IturActivityDTO::class.java) } returns ACTIVITY_DTO
            },
        )
        val hydratedRepository = FirebaseActivityRepository(
            firestore,
            mockk<BackendHealthReporter>(relaxed = true),
            transactionExecutor,
            admissionGateway,
        ) {
            CanonicalParticipantState(
                signals = mapOf(departedParticipant.value to ParticipantSignal.NEEDS_HELP.name),
            )
        }

        val result = hydratedRepository.getActivity(ACTIVITY_ID)

        assertIs<DataResult.Success<IturActivity>>(result)
        assertTrue(departedParticipant !in result.data.participantSignals)
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

private fun emptyQuerySnapshot(): QuerySnapshot = mockk {
    every { documents } returns emptyList()
}

private fun postLeavePermissionDenied() = FirebaseFirestoreException(
    "PERMISSION_DENIED: Missing or insufficient permissions.",
    FirebaseFirestoreException.Code.PERMISSION_DENIED,
)
