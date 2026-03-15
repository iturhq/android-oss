/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.data.repository

import com.nohex.itur.core.domain.id.IturActivityId
import com.nohex.itur.core.domain.id.UserId
import com.nohex.itur.core.model.IturActivity
import com.nohex.itur.core.model.IturActivityStatus
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private val ORGANIZER_ID = UserId("organizer1")
private val PARTICIPANT_ID = UserId("participant1")
private val ACTIVITY_ID = IturActivityId("TestActivity00000001")
private val OTHER_ID = IturActivityId("OtherActivity0000001")

private val ACTIVITY = IturActivity(
    id = ACTIVITY_ID,
    status = IturActivityStatus.ONGOING,
    organizerId = ORGANIZER_ID,
    participantIds = listOf(ORGANIZER_ID, PARTICIPANT_ID),
)

class FakeActivityRepositoryTest {

    private fun repository(vararg activities: IturActivity) = FakeActivityRepository(initialActivities = activities.toList())

    // --- getActivity ---

    @Test
    fun `GIVEN a seeded activity WHEN getting it by ID THEN returns Success with the activity`() = runBlocking {
        val result = repository(ACTIVITY).getActivity(ACTIVITY_ID)
        assertIs<DataResult.Success<IturActivity>>(result)
        assertEquals(ACTIVITY, result.data)
    }

    @Test
    fun `GIVEN no matching activity WHEN getting by ID THEN returns NotFound`() {
        runBlocking {
            val result = repository().getActivity(ACTIVITY_ID)
            assertIs<DataResult.NotFound>(result)
        }
    }

    // --- getActivities ---

    @Test
    fun `GIVEN activities WHEN filtering by organizer THEN returns only their activities`() = runBlocking {
        val other = ACTIVITY.copy(
            id = OTHER_ID,
            organizerId = UserId("other-organizer"),
        )
        val result = repository(ACTIVITY, other)
            .getActivities(ActivityFilter.ByOrganizer(ORGANIZER_ID))
        assertIs<DataResult.Success<List<IturActivity>>>(result)
        assertEquals(listOf(ACTIVITY), result.data)
    }

    @Test
    fun `GIVEN no matching activities WHEN filtering by organizer THEN returns empty list`() {
        runBlocking {
            val result = repository()
                .getActivities(ActivityFilter.ByOrganizer(ORGANIZER_ID))
            assertIs<DataResult.Success<List<IturActivity>>>(result)
            assertTrue(result.data.isEmpty())
        }
    }

    @Test
    fun `GIVEN mixed-status activities WHEN filtering ongoing by organizer THEN returns only ongoing`() = runBlocking {
        val draft = ACTIVITY.copy(
            id = OTHER_ID,
            status = IturActivityStatus.DRAFT,
        )
        val result = repository(ACTIVITY, draft)
            .getActivities(ActivityFilter.OngoingByOrganizer(ORGANIZER_ID))
        assertIs<DataResult.Success<List<IturActivity>>>(result)
        assertEquals(listOf(ACTIVITY), result.data)
    }

    // --- createActivity ---

    @Test
    fun `WHEN creating an activity THEN returns Success with a valid 20-char alphanumeric ID`() = runBlocking {
        val result = repository().createActivity(ORGANIZER_ID)
        assertIs<DataResult.Success<IturActivity>>(result)
        val id = result.data.id.value
        assertEquals(20, id.length)
        assertTrue(id.all { it.isLetterOrDigit() })
    }

    @Test
    fun `WHEN creating an activity THEN the organizer ID is set correctly`() = runBlocking {
        val result = repository().createActivity(ORGANIZER_ID)
        assertIs<DataResult.Success<IturActivity>>(result)
        assertEquals(ORGANIZER_ID, result.data.organizerId)
    }

    // --- addParticipant ---

    @Test
    fun `GIVEN an activity WHEN adding a participant THEN they appear in the participant list`() = runBlocking {
        val newUser = UserId("new-user")
        val result = repository(ACTIVITY).addParticipant(ACTIVITY_ID, newUser)
        assertIs<DataResult.Success<IturActivity>>(result)
        assertTrue(newUser in result.data.participantIds)
    }

    @Test
    fun `GIVEN no matching activity WHEN adding a participant THEN throws IllegalArgumentException`() = runBlocking {
        try {
            repository().addParticipant(ACTIVITY_ID, PARTICIPANT_ID)
            error("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    // --- removeParticipant ---

    @Test
    fun `GIVEN an activity WHEN removing a participant THEN they are absent from the participant list`() = runBlocking {
        val result = repository(ACTIVITY).removeParticipant(ACTIVITY_ID, PARTICIPANT_ID)
        assertIs<DataResult.Success<IturActivity>>(result)
        assertTrue(PARTICIPANT_ID !in result.data.participantIds)
    }

    @Test
    fun `GIVEN no matching activity WHEN removing a participant THEN returns NotFound`() {
        runBlocking {
            val result = repository().removeParticipant(ACTIVITY_ID, PARTICIPANT_ID)
            assertIs<DataResult.NotFound>(result)
        }
    }

    // --- updateActivityStatus ---

    @Test
    fun `GIVEN an activity WHEN updating its status THEN the new status is reflected`() = runBlocking {
        val result = repository(ACTIVITY)
            .updateActivityStatus(ACTIVITY_ID, IturActivityStatus.FINISHED)
        assertIs<DataResult.Success<IturActivity>>(result)
        assertEquals(IturActivityStatus.FINISHED, result.data.status)
    }

    @Test
    fun `GIVEN no matching activity WHEN updating status THEN returns NotFound`() {
        runBlocking {
            val result = repository()
                .updateActivityStatus(ACTIVITY_ID, IturActivityStatus.FINISHED)
            assertIs<DataResult.NotFound>(result)
        }
    }

    // --- deleteActivity ---

    @Test
    fun `GIVEN an activity WHEN deleting it THEN it is no longer retrievable`() {
        runBlocking {
            val repo = repository(ACTIVITY)
            repo.deleteActivity(ACTIVITY_ID)
            assertIs<DataResult.NotFound>(repo.getActivity(ACTIVITY_ID))
        }
    }

    @Test
    fun `GIVEN an activity WHEN deleting it THEN returns Success with the deleted activity`() = runBlocking {
        val result = repository(ACTIVITY).deleteActivity(ACTIVITY_ID)
        assertIs<DataResult.Success<IturActivity>>(result)
        assertEquals(ACTIVITY, result.data)
    }

    @Test
    fun `GIVEN no matching activity WHEN deleting THEN returns NotFound`() {
        runBlocking {
            val result = repository().deleteActivity(ACTIVITY_ID)
            assertIs<DataResult.NotFound>(result)
        }
    }

    // --- requestAttention ---

    @Test
    fun `GIVEN an activity WHEN requesting attention THEN no exception is thrown`() = runBlocking {
        repository(ACTIVITY).requestAttention(ACTIVITY_ID, PARTICIPANT_ID)
    }
}
