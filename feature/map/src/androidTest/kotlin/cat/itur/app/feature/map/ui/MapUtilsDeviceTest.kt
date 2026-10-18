/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map.ui

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/** Exercises the pure camera-framing policy in the same device coverage run as its map callers. */
class MapUtilsDeviceTest {

    @Test
    fun framingHistoryKeepsFreshDistinctSamples() {
        val first = location(longitude = 2.0, time = 1_000)
        val second = location(longitude = 2.0001, time = 6_000)
        val third = location(longitude = 2.0002, time = 11_000)
        val fourth = location(longitude = 2.0003, time = 16_000)

        val history = listOf(first, second, third, fourth)
            .fold(emptyList<RecentLocation>()) { samples, sample -> appendRecentLocation(samples, sample) }

        assertEquals(listOf(6_000L, 11_000L, 16_000L), history.map(RecentLocation::timeMillis))
        assertEquals(listOf(first), appendRecentLocation(listOf(first), first))
        assertEquals(listOf(fourth), appendRecentLocation(listOf(first), fourth))
    }

    @Test
    fun framingDistanceUsesMotionReportedSpeedAndBounds() {
        val walking = selfFramingDistanceMeters(movingLocations(0.00009))
        val running = selfFramingDistanceMeters(movingLocations(0.00027))
        val cycling = selfFramingDistanceMeters(movingLocations(0.00072))

        assertTrue(walking in MIN_SELF_FRAMING_DISTANCE_METERS..MAX_SELF_FRAMING_DISTANCE_METERS)
        assertTrue(walking < running && running < cycling)
        assertEquals(
            150.0,
            selfFramingDistanceMeters(listOf(location(speed = 2f), location(speed = 6f))),
            0.001,
        )
        assertEquals(MIN_SELF_FRAMING_DISTANCE_METERS, selfFramingDistanceMeters(emptyList()), 0.001)
        assertEquals(
            MAX_SELF_FRAMING_DISTANCE_METERS,
            selfFramingDistanceMeters(listOf(location(speed = 100f))),
            0.001,
        )
    }

    @Test
    fun framingZoomUsesDirectionAndSafeViewportFallback() {
        val walking = selfFramingDistanceMeters(movingLocations(0.00009))
        val cycling = selfFramingDistanceMeters(movingLocations(0.00072))

        assertTrue(
            selfFramingZoom(41.0, 1_920, cycling, isDirectionOfTravel = false) <
                selfFramingZoom(41.0, 1_920, walking, isDirectionOfTravel = false),
        )
        assertEquals(
            selfFramingZoom(41.0, 1_000, 100.0, isDirectionOfTravel = false),
            selfFramingZoom(41.0, 0, 100.0, isDirectionOfTravel = false),
            0.001,
        )
    }

    @Test
    fun framingTargetCentersNorthUpAndMovesAheadWhenTravelling() {
        val previous = location(latitude = 41.0, longitude = 2.0, time = 1_000)
        val current = location(latitude = 41.01, longitude = 2.0, time = 2_000, bearing = 0f)

        assertEquals(
            MapPoint(current.latitude, current.longitude),
            selfFramingTarget(current, listOf(previous, current), 100.0, isDirectionOfTravel = false),
        )
        assertTrue(
            selfFramingTarget(current, listOf(previous, current), 100.0, isDirectionOfTravel = true).latitude >
                current.latitude,
        )
    }

    private fun movingLocations(distanceDegrees: Double) = listOf(
        location(longitude = 2.0, time = 0),
        location(longitude = 2.0 + distanceDegrees, time = 5_000),
        location(longitude = 2.0 + 2 * distanceDegrees, time = 10_000),
    )

    private fun location(
        latitude: Double = 41.0,
        longitude: Double = 2.0,
        time: Long = 0,
        bearing: Float? = null,
        speed: Float? = null,
    ) = RecentLocation(latitude, longitude, time, speed, bearing)
}
