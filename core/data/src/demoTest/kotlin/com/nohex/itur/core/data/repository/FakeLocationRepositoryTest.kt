/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.data.repository

import com.nohex.itur.core.domain.id.IturActivityId
import com.nohex.itur.core.domain.id.UserId
import com.nohex.itur.core.domain.model.User
import com.nohex.itur.core.model.IturActivity
import com.nohex.itur.core.model.IturActivityStatus
import com.nohex.itur.core.model.Location
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlin.math.sqrt
import kotlin.test.BeforeTest
import kotlin.test.DefaultAsserter.assertTrue
import kotlin.test.Test
import kotlin.test.assertTrue

class FakeLocationRepositoryTest {

    private lateinit var activityRepository: ActivityRepository
    private lateinit var locationRepository: FakeLocationRepository

    private val activityId = IturActivityId("activity001234567890")
    private val userId = UserId("user-123")
    private val meetingPoint = Location(51.4792842, 0.0)

    private val testParticipants = setOf(
        User.AnonymousUser(
            id = userId,
        ),
    )

    private val testActivity = IturActivity(
        id = activityId,
        status = IturActivityStatus.DRAFT,
        organizerId = UserId("organizer-1"),
        participantIds = testParticipants.map { it.id },
    )

    @BeforeTest
    fun setup() {
        activityRepository = mockk()

        coEvery { activityRepository.getActivities(ActivityFilter.ById(activityId)) } returns listOf(
            testActivity,
        )

        locationRepository = FakeLocationRepository(activityRepository)
    }

    @Test
    fun `GIVEN no activity WHEN locations are fetched THEN empty list is returned`() = runBlocking {
        coEvery { activityRepository.getActivities(ActivityFilter.ById(activityId)) } returns emptyList()

        val locations = locationRepository.getForActivity(activityId)

        assertTrue(locations.isEmpty())
    }

    @Test
    fun `GIVEN no participants WHEN locations are fetched THEN empty list is returned`() = runBlocking {
        coEvery { activityRepository.getActivities(ActivityFilter.ById(activityId)) } returns emptyList()

        val locations = locationRepository.getForActivity(activityId)

        assertTrue(locations.isEmpty())
    }

    @Test
    fun `GIVEN participant with meeting point WHEN locations are fetched THEN location is within 50m`() = runBlocking {
        val locations = locationRepository.getForActivity(activityId)

        val location = locations.first().location
        val distance = calculateDistance(meetingPoint, location)

        assertTrue("Expected distance <= 50m, got $distance", distance <= 55.0)
    }

    @Test
    fun `GIVEN participant with previous location WHEN locations are fetched again THEN location is jittered within 5m`() = runBlocking {
        val firstLocation = locationRepository.getForActivity(activityId).first().location
        val secondLocation = locationRepository.getForActivity(activityId).first().location

        val distance = calculateDistance(firstLocation, secondLocation)

        assertTrue("Expected jitter distance <= 5m, got $distance", distance <= 5.0)
    }

    /**
     * Naïvely calculates the distance between to locations.
     *
     * For the distances involved in these tests, the curvature of Earth can be ignored, so
     * no overkill haversine formula or cos(radians(latitude)) adjustment.
     */
    private fun calculateDistance(loc1: Location, loc2: Location): Double {
        val latDiff = (loc1.latitude - loc2.latitude) * METERS_PER_DEGREE
        val lonDiff = (loc1.longitude - loc2.longitude) * METERS_PER_DEGREE

        return sqrt(latDiff * latDiff + lonDiff * lonDiff)
    }
}
