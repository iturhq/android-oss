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

    @Test
    fun `framing uses reported speeds when timestamped movement is unavailable`() {
        val locations = listOf(
            location(latitude = 41.0, longitude = 2.0, time = 0, speed = 2f),
            location(latitude = 41.0, longitude = 2.0, time = 0, speed = 6f),
        )

        assertEquals(150.0, selfFramingDistanceMeters(locations))
    }

    @Test
    fun `framing clamps both extreme speeds and invalid viewport sizes`() {
        val stopped = selfFramingDistanceMeters(emptyList())
        val fast = selfFramingDistanceMeters(
            listOf(location(latitude = 41.0, longitude = 2.0, time = 0, speed = 100f)),
        )

        assertEquals(MIN_SELF_FRAMING_DISTANCE_METERS, stopped)
        assertEquals(MAX_SELF_FRAMING_DISTANCE_METERS, fast)
        assertEquals(
            selfFramingZoom(41.0, 1_000, 100.0, isDirectionOfTravel = false),
            selfFramingZoom(41.0, 0, 100.0, isDirectionOfTravel = false),
        )
    }

    @Test
    fun `history ignores duplicate samples and discards stale reports`() {
        val sample = location(latitude = 41.0, longitude = 2.0, time = 1_000)
        val fresh = location(latitude = 41.0, longitude = 2.1, time = 12_000)

        assertEquals(listOf(sample), appendRecentLocation(listOf(sample), sample))
        assertEquals(listOf(fresh), appendRecentLocation(listOf(sample), fresh))
    }

    @Test
    fun `north up keeps the pointer centered while inferred travel bearing moves it`() {
        val previous = location(latitude = 41.0, longitude = 2.0, time = 1_000)
        val current = location(latitude = 41.01, longitude = 2.0, time = 2_000)

        assertEquals(
            MapPoint(current.latitude, current.longitude),
            selfFramingTarget(current, listOf(previous, current), 100.0, isDirectionOfTravel = false),
        )
        assertTrue(
            selfFramingTarget(current, listOf(previous, current), 100.0, isDirectionOfTravel = true).latitude >
                current.latitude,
        )
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
        speed: Float? = null,
    ): RecentLocation = RecentLocation(
        latitude = latitude,
        longitude = longitude,
        timeMillis = time,
        speedMetersPerSecond = speed,
        bearingDegrees = bearing,
    )
}
