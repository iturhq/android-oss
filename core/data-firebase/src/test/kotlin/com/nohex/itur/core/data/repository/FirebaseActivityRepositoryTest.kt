/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.nohex.itur.core.domain.id.IturActivityId
import com.nohex.itur.core.domain.id.UserId
import com.nohex.itur.core.model.IturActivity
import com.nohex.itur.core.model.IturActivityStatus
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

class FirebaseActivityRepositoryTest {
    private val activitiesCollection = mockk<CollectionReference>()
    private val firestore = mockk<FirebaseFirestore> {
        every { collection(FirestoreCollections.ACTIVITIES) } returns activitiesCollection
    }
    private val repository = FirebaseActivityRepository(firestore)

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
        every { activitiesCollection.document(ACTIVITY_ID.value) } returns docRef
        val updates = slot<Map<String, Any>>()
        every { docRef.update(capture(updates)) } returns successfulTask(null)
        val snapshot = mockk<DocumentSnapshot> {
            every { exists() } returns true
            every { toObject(IturActivityDTO::class.java) } returns ACTIVITY_DTO.copy(status = "READY")
        }
        every { docRef.get() } returns successfulTask(snapshot)

        val result = repository.updateActivityStatus(ACTIVITY_ID, IturActivityStatus.READY)

        assertIs<DataResult.Success<IturActivity>>(result)
        assertEquals(setOf("status"), updates.captured.keys)
    }

    @Test
    fun `GIVEN a terminal status WHEN updating THEN finishedOn is also set`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        every { activitiesCollection.document(ACTIVITY_ID.value) } returns docRef
        val updates = slot<Map<String, Any>>()
        every { docRef.update(capture(updates)) } returns successfulTask(null)
        val snapshot = mockk<DocumentSnapshot> {
            every { exists() } returns true
            every { toObject(IturActivityDTO::class.java) } returns ACTIVITY_DTO.copy(status = "FINISHED")
        }
        every { docRef.get() } returns successfulTask(snapshot)

        val result = repository.updateActivityStatus(ACTIVITY_ID, IturActivityStatus.FINISHED)

        assertIs<DataResult.Success<IturActivity>>(result)
        assertEquals(setOf("status", "finishedOn"), updates.captured.keys)
    }

    @Test
    fun `GIVEN Firestore throws WHEN updating status THEN returns Error`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        every { activitiesCollection.document(ACTIVITY_ID.value) } returns docRef
        every { docRef.update(any<Map<String, Any>>()) } returns failedTask(RuntimeException("denied"))

        val result = repository.updateActivityStatus(ACTIVITY_ID, IturActivityStatus.FINISHED)

        assertIs<DataResult.Error>(result)
        Unit
    }

    // --- createActivity ---

    @Test
    fun `WHEN creating an activity THEN it is stored as ONGOING with the organizer as sole participant`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        every { activitiesCollection.document() } returns docRef
        every { docRef.id } returns "NewActivity000000001"
        val dto = slot<IturActivityDTO>()
        every { docRef.set(capture(dto)) } returns successfulTask(null)

        val result = repository.createActivity(ORGANIZER_ID)

        assertIs<DataResult.Success<IturActivity>>(result)
        assertEquals("NewActivity000000001", result.data.id.value)
        assertEquals(ORGANIZER_ID, result.data.organizerId)
        assertEquals(listOf(ORGANIZER_ID), result.data.participantIds)
        assertEquals(IturActivityStatus.ONGOING, result.data.status)
        assertEquals("ONGOING", dto.captured.status)
    }

    @Test
    fun `GIVEN Firestore throws WHEN creating an activity THEN the exception propagates`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        every { activitiesCollection.document() } returns docRef
        every { docRef.id } returns "NewActivity000000001"
        every { docRef.set(any<IturActivityDTO>()) } returns failedTask(RuntimeException("no network"))

        assertFailsWith<RuntimeException> { repository.createActivity(ORGANIZER_ID) }
        Unit
    }

    // --- deleteActivity ---

    @Test
    fun `GIVEN an existing document WHEN deleting it THEN it is deleted and returned`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        every { activitiesCollection.document(ACTIVITY_ID.value) } returns docRef
        val snapshot = mockk<DocumentSnapshot> {
            every { exists() } returns true
            every { toObject(IturActivityDTO::class.java) } returns ACTIVITY_DTO
        }
        every { docRef.get() } returns successfulTask(snapshot)
        every { docRef.delete() } returns successfulTask(null)

        val result = repository.deleteActivity(ACTIVITY_ID)

        assertIs<DataResult.Success<IturActivity>>(result)
        verify(exactly = 1) { docRef.delete() }
    }

    @Test
    fun `GIVEN no document WHEN deleting THEN returns NotFound without calling delete`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        every { activitiesCollection.document(ACTIVITY_ID.value) } returns docRef
        // deleteActivity() calls toObject() unconditionally (not gated behind exists()), using its
        // nullability -- matching a real non-existent DocumentSnapshot -- to detect not-found.
        val snapshot = mockk<DocumentSnapshot> {
            every { exists() } returns false
            every { toObject(IturActivityDTO::class.java) } returns null
        }
        every { docRef.get() } returns successfulTask(snapshot)

        val result = repository.deleteActivity(ACTIVITY_ID)

        assertIs<DataResult.NotFound>(result)
        verify(exactly = 0) { docRef.delete() }
    }

    @Test
    fun `GIVEN Firestore throws WHEN deleting an activity THEN returns Error rather than propagating`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        every { activitiesCollection.document(ACTIVITY_ID.value) } returns docRef
        every { docRef.get() } returns failedTask(RuntimeException("offline"))

        val result = repository.deleteActivity(ACTIVITY_ID)

        assertIs<DataResult.Error>(result)
        Unit
    }

    // --- addParticipant / removeParticipant ---

    @Test
    fun `WHEN adding a participant THEN participantIds is updated with an arrayUnion`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        every { activitiesCollection.document(ACTIVITY_ID.value) } returns docRef
        val fieldValue = slot<FieldValue>()
        every { docRef.update("participantIds", capture(fieldValue)) } returns successfulTask(null)
        val snapshot = mockk<DocumentSnapshot> {
            every { toObject(IturActivityDTO::class.java) } returns ACTIVITY_DTO
        }
        every { docRef.get() } returns successfulTask(snapshot)

        val result = repository.addParticipant(ACTIVITY_ID, PARTICIPANT_ID)

        assertIs<DataResult.Success<IturActivity>>(result)
        assertEquals("ArrayUnionFieldValue", fieldValue.captured.javaClass.simpleName)
    }

    @Test
    fun `WHEN removing a participant THEN participantIds is updated with an arrayRemove`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        every { activitiesCollection.document(ACTIVITY_ID.value) } returns docRef
        val fieldValue = slot<FieldValue>()
        every { docRef.update("participantIds", capture(fieldValue)) } returns successfulTask(null)
        val snapshot = mockk<DocumentSnapshot> {
            every { toObject(IturActivityDTO::class.java) } returns ACTIVITY_DTO
        }
        every { docRef.get() } returns successfulTask(snapshot)

        val result = repository.removeParticipant(ACTIVITY_ID, PARTICIPANT_ID)

        assertIs<DataResult.Success<IturActivity>>(result)
        assertEquals("ArrayRemoveFieldValue", fieldValue.captured.javaClass.simpleName)
    }

    @Test
    fun `GIVEN Firestore throws WHEN updating participants THEN returns Error rather than propagating`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        every { activitiesCollection.document(ACTIVITY_ID.value) } returns docRef
        every { docRef.update("participantIds", any<FieldValue>()) } returns failedTask(RuntimeException("denied"))

        val result = repository.addParticipant(ACTIVITY_ID, PARTICIPANT_ID)

        assertIs<DataResult.Error>(result)
        Unit
    }

    // --- requestAttention ---

    @Test
    fun `WHEN requesting attention THEN attentionRequests is updated with an arrayUnion`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        every { activitiesCollection.document(ACTIVITY_ID.value) } returns docRef
        val fieldValue = slot<FieldValue>()
        every { docRef.update("attentionRequests", capture(fieldValue)) } returns successfulTask(null)

        repository.requestAttention(ACTIVITY_ID, PARTICIPANT_ID)

        assertEquals("ArrayUnionFieldValue", fieldValue.captured.javaClass.simpleName)
    }

    @Test
    fun `GIVEN Firestore throws WHEN requesting attention THEN the exception propagates`() = runBlocking {
        val docRef = mockk<DocumentReference>()
        every { activitiesCollection.document(ACTIVITY_ID.value) } returns docRef
        every { docRef.update("attentionRequests", any<FieldValue>()) } returns failedTask(RuntimeException("denied"))

        assertFailsWith<RuntimeException> { repository.requestAttention(ACTIVITY_ID, PARTICIPANT_ID) }
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
