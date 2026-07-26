/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.data.repository

import com.nohex.itur.core.domain.id.IturActivityId
import com.nohex.itur.core.domain.id.UserId
import com.nohex.itur.core.model.IturActivity
import com.nohex.itur.core.model.IturActivityStatus
import com.nohex.itur.core.model.Location
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val ACTIVITY_ID = IturActivityId("TestActivity00000001")
private val ORGANIZER_ID = UserId("organizer1")
private val PARTICIPANT_ID = UserId("participant1")

private val ACTIVITY = IturActivity(
    id = ACTIVITY_ID,
    status = IturActivityStatus.ONGOING,
    organizerId = ORGANIZER_ID,
    participantIds = listOf(ORGANIZER_ID, PARTICIPANT_ID),
)

// Max jitter applied by getForActivity (5m / 111_000 m/deg).
private const val MAX_JITTER_DEGREES = 5.0 / METERS_PER_DEGREE

class FakeLocationRepositoryTest {

    private fun activityRepo(vararg activities: IturActivity) = FakeActivityRepository(initialActivities = activities.toList())

    private fun locationRepo(
        activityRepository: ActivityRepository,
        initialLocations: Map<IturActivityId, Map<UserId, Location>> = emptyMap(),
    ) = FakeLocationRepository(
        activityRepository = activityRepository,
        initialLocations = initialLocations,
    )

    // --- getForActivity ---

    @Test
    fun `GIVEN an unknown activity WHEN getting locations THEN returns an empty list`() = runBlocking {
        val repo = locationRepo(activityRepo())
        assertTrue(repo.getForActivity(ACTIVITY_ID).isEmpty())
    }

    @Test
    fun `GIVEN a known activity with participants WHEN getting locations THEN returns one entry per participant`() = runBlocking {
        val repo = locationRepo(activityRepo(ACTIVITY))
        val locations = repo.getForActivity(ACTIVITY_ID)
        assertEquals(ACTIVITY.participantIds.size, locations.size)
    }

    @Test
    fun `GIVEN a known activity WHEN getting locations twice THEN all participant IDs are present`() = runBlocking {
        val repo = locationRepo(activityRepo(ACTIVITY))
        val locations = repo.getForActivity(ACTIVITY_ID)
        val returnedIds = locations.map { it.userId }.toSet()
        assertTrue(ACTIVITY.participantIds.all { it in returnedIds })
    }

    // --- updateForParticipant ---

    @Test
    fun `GIVEN a stored location WHEN getting locations THEN the result is close to the stored position`() = runBlocking {
        val stored = Location(latitude = 51.4793, longitude = 0.0001)
        val repo = locationRepo(activityRepo(ACTIVITY))
        repo.updateForParticipant(ORGANIZER_ID, ACTIVITY_ID, stored)

        val returned = repo.getForActivity(ACTIVITY_ID)
            .first { it.userId == ORGANIZER_ID }
            .location

        assertTrue(abs(returned.latitude - stored.latitude) <= MAX_JITTER_DEGREES)
        assertTrue(abs(returned.longitude - stored.longitude) <= MAX_JITTER_DEGREES)
    }

    // --- removeForActivity ---

    @Test
    fun `GIVEN stored locations WHEN removing them THEN the activity's participant count is unchanged on next fetch`() = runBlocking {
        // getForActivity regenerates positions from scratch after removal;
        // the participant count comes from the activity repo, not the location map.
        val repo = locationRepo(activityRepo(ACTIVITY))
        repo.getForActivity(ACTIVITY_ID) // seed initial positions
        repo.removeForActivity(ACTIVITY_ID)
        assertEquals(
            ACTIVITY.participantIds.size,
            repo.getForActivity(ACTIVITY_ID).size,
        )
    }

    @Test
    fun `GIVEN a location stored before removal WHEN updating after removal THEN new location is reflected`() = runBlocking {
        val original = Location(latitude = 51.0, longitude = 0.0)
        val updated = Location(latitude = 52.0, longitude = 1.0)
        val repo = locationRepo(activityRepo(ACTIVITY))

        repo.updateForParticipant(ORGANIZER_ID, ACTIVITY_ID, original)
        repo.removeForActivity(ACTIVITY_ID)
        repo.updateForParticipant(ORGANIZER_ID, ACTIVITY_ID, updated)

        val returned = repo.getForActivity(ACTIVITY_ID)
            .first { it.userId == ORGANIZER_ID }
            .location

        // The returned location should be close to `updated`, not `original`.
        assertTrue(abs(returned.latitude - updated.latitude) <= MAX_JITTER_DEGREES)
        assertTrue(abs(returned.longitude - updated.longitude) <= MAX_JITTER_DEGREES)
    }
}
