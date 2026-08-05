/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.data.repository

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.nohex.itur.core.domain.id.IturActivityId
import com.nohex.itur.core.domain.id.UserId
import com.nohex.itur.core.domain.model.User
import com.nohex.itur.core.model.Location
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private val ACTIVITY_ID = IturActivityId("TestActivity00000001")
private val USER_ID = UserId("user1")
private val NEW_LOCATION = Location(latitude = 51.5, longitude = -0.1)

/**
 * `removeForActivity` and `updateForParticipant` deliberately have no try/catch of their own
 * (fixed under AOSS-355C: they used to swallow failures that `MapViewModel`'s own handling
 * never got a chance to see) -- every test here that touches Firestore also confirms an
 * exception from it propagates out uncaught, to guard that fix.
 */
class FirebaseLocationRepositoryTest {
    private val locationsCollection = mockk<CollectionReference>()
    private val firestore = mockk<FirebaseFirestore> {
        every { collection(FirestoreCollections.LOCATIONS) } returns locationsCollection
    }
    private val userRepository = mockk<UserRepository>()
    private val repository = FirebaseLocationRepository(firestore, userRepository)

    // --- getForActivity ---

    @Test
    fun `GIVEN matching locations WHEN getting them for an activity THEN resolves the participant's real name`() = runBlocking {
        val query = mockk<Query>()
        every { locationsCollection.whereEqualTo("activityId", ACTIVITY_ID.value) } returns query
        val dto = ParticipantLocationDTO(
            activityId = ACTIVITY_ID.value,
            userId = USER_ID.value,
            location = GeoPoint(51.4, -0.2),
        )
        val querySnapshot = mockk<QuerySnapshot> { every { toObjects(ParticipantLocationDTO::class.java) } returns listOf(dto) }
        every { query.get() } returns successfulTask(querySnapshot)
        coEvery { userRepository.getAll(listOf(USER_ID)) } returns listOf(
            User.RegisteredUser(id = USER_ID, name = "Ada Lovelace", email = "ada@example.com"),
        )

        val result = repository.getForActivity(ACTIVITY_ID)

        assertEquals(1, result.size)
        assertEquals(USER_ID, result[0].userId)
        assertEquals("Ada Lovelace", result[0].userName)
        assertEquals(51.4, result[0].location.latitude)
        assertEquals(-0.2, result[0].location.longitude)
    }

    @Test
    fun `GIVEN a participant's user record is not found WHEN getting locations for an activity THEN the name falls back to the placeholder`() = runBlocking {
        val query = mockk<Query>()
        every { locationsCollection.whereEqualTo("activityId", ACTIVITY_ID.value) } returns query
        val dto = ParticipantLocationDTO(
            activityId = ACTIVITY_ID.value,
            userId = USER_ID.value,
            location = GeoPoint(51.4, -0.2),
        )
        val querySnapshot = mockk<QuerySnapshot> { every { toObjects(ParticipantLocationDTO::class.java) } returns listOf(dto) }
        every { query.get() } returns successfulTask(querySnapshot)
        coEvery { userRepository.getAll(listOf(USER_ID)) } returns emptyList()

        val result = repository.getForActivity(ACTIVITY_ID)

        assertEquals("<Not available>", result[0].userName)
    }

    @Test
    fun `GIVEN no matching locations WHEN getting them for an activity THEN returns an empty list without querying users`() = runBlocking {
        val query = mockk<Query>()
        every { locationsCollection.whereEqualTo("activityId", ACTIVITY_ID.value) } returns query
        val querySnapshot = mockk<QuerySnapshot> { every { toObjects(ParticipantLocationDTO::class.java) } returns emptyList() }
        every { query.get() } returns successfulTask(querySnapshot)

        assertTrue(repository.getForActivity(ACTIVITY_ID).isEmpty())
        coVerify(exactly = 0) { userRepository.getAll(any()) }
    }

    @Test
    fun `GIVEN Firestore throws WHEN getting locations for an activity THEN the exception propagates`() = runBlocking {
        val query = mockk<Query>()
        every { locationsCollection.whereEqualTo("activityId", ACTIVITY_ID.value) } returns query
        every { query.get() } returns failedTask(RuntimeException("offline"))

        assertFailsWith<RuntimeException> { repository.getForActivity(ACTIVITY_ID) }
        Unit
    }

    // --- removeForActivity ---

    @Test
    fun `GIVEN matching locations WHEN removing them for an activity THEN each document is deleted`() = runBlocking {
        val query = mockk<Query>()
        every { locationsCollection.whereEqualTo("activityId", ACTIVITY_ID.value) } returns query
        val doc1 = mockk<DocumentSnapshot>()
        val doc2 = mockk<DocumentSnapshot>()
        val ref1 = mockk<DocumentReference>()
        val ref2 = mockk<DocumentReference>()
        every { doc1.reference } returns ref1
        every { doc2.reference } returns ref2
        every { ref1.delete() } returns successfulTask(null)
        every { ref2.delete() } returns successfulTask(null)
        val querySnapshot = mockk<QuerySnapshot> {
            every { documents } returns listOf(doc1, doc2)
            every { size() } returns 2
        }
        every { query.get() } returns successfulTask(querySnapshot)

        repository.removeForActivity(ACTIVITY_ID)

        verify(exactly = 1) { ref1.delete() }
        verify(exactly = 1) { ref2.delete() }
    }

    @Test
    fun `GIVEN no matching locations WHEN removing them for an activity THEN nothing is deleted`() = runBlocking {
        val query = mockk<Query>()
        every { locationsCollection.whereEqualTo("activityId", ACTIVITY_ID.value) } returns query
        val querySnapshot = mockk<QuerySnapshot> {
            every { documents } returns emptyList()
            every { size() } returns 0
        }
        every { query.get() } returns successfulTask(querySnapshot)

        repository.removeForActivity(ACTIVITY_ID)
        // No exception, no deletions attempted -- nothing further to verify.
    }

    @Test
    fun `GIVEN Firestore throws WHEN removing locations for an activity THEN the exception propagates`() = runBlocking {
        val query = mockk<Query>()
        every { locationsCollection.whereEqualTo("activityId", ACTIVITY_ID.value) } returns query
        every { query.get() } returns failedTask(RuntimeException("offline"))

        assertFailsWith<RuntimeException> { repository.removeForActivity(ACTIVITY_ID) }
        Unit
    }

    // --- updateForParticipant ---

    @Test
    fun `GIVEN no existing record WHEN updating a participant's location THEN a new document is added`() = runBlocking {
        val activityQuery = mockk<Query>()
        val fullQuery = mockk<Query>()
        every { locationsCollection.whereEqualTo("activityId", ACTIVITY_ID.value) } returns activityQuery
        every { activityQuery.whereEqualTo("userId", USER_ID.value) } returns fullQuery
        val empty = mockk<QuerySnapshot> { every { isEmpty } returns true }
        every { fullQuery.get() } returns successfulTask(empty)

        val newRecord = slot<ParticipantLocationDTO>()
        val newRef = mockk<DocumentReference>()
        every { locationsCollection.add(capture(newRecord)) } returns successfulTask(newRef)
        every { newRef.id } returns "new-doc-id"

        repository.updateForParticipant(USER_ID, ACTIVITY_ID, NEW_LOCATION)

        assertEquals(ACTIVITY_ID.value, newRecord.captured.activityId)
        assertEquals(USER_ID.value, newRecord.captured.userId)
        assertEquals(NEW_LOCATION.latitude, newRecord.captured.location.latitude)
        assertEquals(NEW_LOCATION.longitude, newRecord.captured.location.longitude)
    }

    @Test
    fun `GIVEN an existing record WHEN updating a participant's location THEN the same document is updated`() = runBlocking {
        val activityQuery = mockk<Query>()
        val fullQuery = mockk<Query>()
        every { locationsCollection.whereEqualTo("activityId", ACTIVITY_ID.value) } returns activityQuery
        every { activityQuery.whereEqualTo("userId", USER_ID.value) } returns fullQuery
        val existingDoc = mockk<DocumentSnapshot> { every { id } returns "existing-doc-id" }
        val existing = mockk<QuerySnapshot> {
            every { isEmpty } returns false
            every { documents } returns listOf(existingDoc)
        }
        every { fullQuery.get() } returns successfulTask(existing)

        val existingRef = mockk<DocumentReference>()
        every { locationsCollection.document("existing-doc-id") } returns existingRef
        val updates = slot<Map<String, Any>>()
        every { existingRef.update(capture(updates)) } returns successfulTask(null)

        repository.updateForParticipant(USER_ID, ACTIVITY_ID, NEW_LOCATION)

        assertEquals(setOf("location", "updatedOn"), updates.captured.keys)
        val updatedGeoPoint = updates.captured["location"] as GeoPoint
        assertEquals(NEW_LOCATION.latitude, updatedGeoPoint.latitude)
        assertEquals(NEW_LOCATION.longitude, updatedGeoPoint.longitude)
        verify(exactly = 0) { locationsCollection.add(any<ParticipantLocationDTO>()) }
    }

    @Test
    fun `GIVEN Firestore throws WHEN updating a participant's location THEN the exception propagates`() = runBlocking {
        val activityQuery = mockk<Query>()
        val fullQuery = mockk<Query>()
        every { locationsCollection.whereEqualTo("activityId", ACTIVITY_ID.value) } returns activityQuery
        every { activityQuery.whereEqualTo("userId", USER_ID.value) } returns fullQuery
        every { fullQuery.get() } returns failedTask(RuntimeException("offline"))

        assertFailsWith<RuntimeException> {
            repository.updateForParticipant(USER_ID, ACTIVITY_ID, NEW_LOCATION)
        }
        Unit
    }
}
