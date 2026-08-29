/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map.ui

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SelfFramingTest {

    @Test
    fun `recent samples retain at most three readings from ten seconds`() {
        val first = location(latitude = 41.0, longitude = 2.0, time = 1_000)
        val second = location(latitude = 41.0, longitude = 2.0001, time = 6_000)
        val third = location(latitude = 41.0, longitude = 2.0002, time = 11_000)
        val fourth = location(latitude = 41.0, longitude = 2.0003, time = 16_000)

        val history = listOf(first, second, third, fourth)
            .fold(emptyList<RecentLocation>()) { samples, sample ->
                appendRecentLocation(samples, sample)
            }

        assertEquals(listOf(6_000L, 11_000L, 16_000L), history.map(RecentLocation::timeMillis))
    }

    @Test
    fun `walking running and cycling speed widen framing within the target band`() {
        val walking = selfFramingDistanceMeters(movingLocations(distanceDegrees = 0.00009))
        val running = selfFramingDistanceMeters(movingLocations(distanceDegrees = 0.00027))
        val cycling = selfFramingDistanceMeters(movingLocations(distanceDegrees = 0.00072))

        assertTrue(walking in MIN_SELF_FRAMING_DISTANCE_METERS..MAX_SELF_FRAMING_DISTANCE_METERS)
        assertTrue(running in MIN_SELF_FRAMING_DISTANCE_METERS..MAX_SELF_FRAMING_DISTANCE_METERS)
        assertTrue(cycling in MIN_SELF_FRAMING_DISTANCE_METERS..MAX_SELF_FRAMING_DISTANCE_METERS)
        assertTrue(walking < running)
        assertTrue(running < cycling)
    }

    @Test
    fun `faster movement selects a wider lower-zoom frame`() {
        val walkingDistance = selfFramingDistanceMeters(movingLocations(distanceDegrees = 0.00009))
        val cyclingDistance = selfFramingDistanceMeters(movingLocations(distanceDegrees = 0.00072))

        val walkingZoom = selfFramingZoom(41.0, 1_920, walkingDistance, isDirectionOfTravel = false)
        val cyclingZoom = selfFramingZoom(41.0, 1_920, cyclingDistance, isDirectionOfTravel = false)

        assertTrue(cyclingZoom < walkingZoom)
    }

    @Test
    fun `direction of travel puts the camera target ahead of the pointer`() {
        val current = location(latitude = 41.0, longitude = 2.0, time = 10_000, bearing = 0f)

        val target = selfFramingTarget(
            location = current,
            recentLocations = listOf(current),
            framingDistanceMeters = 100.0,
            isDirectionOfTravel = true,
        )

        assertTrue(target.latitude > current.latitude)
    }

    private fun movingLocations(distanceDegrees: Double): List<RecentLocation> = listOf(
        location(latitude = 41.0, longitude = 2.0, time = 0),
        location(latitude = 41.0, longitude = 2.0 + distanceDegrees, time = 5_000),
        location(latitude = 41.0, longitude = 2.0 + 2 * distanceDegrees, time = 10_000),
    )

    private fun location(
        latitude: Double,
        longitude: Double,
        time: Long,
        bearing: Float? = null,
    ): RecentLocation = RecentLocation(
        latitude = latitude,
        longitude = longitude,
        timeMillis = time,
        speedMetersPerSecond = null,
        bearingDegrees = bearing,
    )
}
