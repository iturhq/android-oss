/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map.ui

import android.location.Location
import androidx.compose.ui.geometry.Rect
import cat.itur.app.core.data.TestFixtures
import cat.itur.app.core.model.GpxLine
import cat.itur.app.core.model.GpxPoint
import cat.itur.app.core.model.GpxTrack
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CameraFitInsetsTest {

    @Test
    fun `live lane bounds define separate left and right fit insets`() {
        val insets = cameraFitInsets(
            mapBounds = Rect(0f, 40f, 1080f, 2400f),
            leftControlBounds = Rect(42f, 1900f, 189f, 2358f),
            rightControlBounds = Rect(891f, 1900f, 1038f, 2358f),
            horizontalFallback = 180,
            verticalPadding = 42,
        )

        assertEquals(CameraFitInsets(left = 231, top = 42, right = 231, bottom = 42), insets)
    }

    @Test
    fun `unmeasured lanes use the density scaled fallback`() {
        assertEquals(
            CameraFitInsets(left = 264, top = 96, right = 264, bottom = 96),
            cameraFitInsets(null, null, null, horizontalFallback = 264, verticalPadding = 96),
        )
    }

    @Test
    fun `multi point group fit forwards all points and four edge insets`() {
        val expectedInsets = CameraFitInsets(left = 231, top = 42, right = 250, bottom = 42)
        var capturedBounds: LatLngBounds? = null
        var capturedInsets: CameraFitInsets? = null

        zoomOnGroup(
            map = mockk<MapLibreMap>(),
            participantLocations = TestFixtures.ongoingActivityLocations,
            currentLocation = null,
            insets = expectedInsets,
            fitBounds = { _, bounds, insets ->
                capturedBounds = bounds
                capturedInsets = insets
            },
        )

        assertEquals(expectedInsets, capturedInsets)
        TestFixtures.ongoingActivityLocations.forEach { participant ->
            val location = participant.location
            assertTrue(
                capturedBounds!!.contains(
                    org.maplibre.android.geometry.LatLng(location.latitude, location.longitude),
                ),
            )
        }
    }

    @Test
    fun `empty group skips camera work and a single point uses a fixed zoom`() {
        val map = mockk<MapLibreMap>(relaxed = true)
        var fitCalls = 0

        zoomOnGroup(map, emptyList(), null, CameraFitInsets(0, 0, 0, 0)) { _, _, _ -> fitCalls++ }
        zoomOnGroup(
            map,
            emptyList(),
            TestFixtures.ongoingActivityLocations.first().location.toAndroidLocation(),
            CameraFitInsets(0, 0, 0, 0),
        )

        assertEquals(0, fitCalls)
    }

    @Test
    fun `group fitting tolerates a map bounds failure`() {
        zoomOnGroup(
            map = mockk<MapLibreMap>(),
            participantLocations = TestFixtures.ongoingActivityLocations,
            currentLocation = null,
            insets = CameraFitInsets(0, 0, 0, 0),
            fitBounds = { _, _, _ -> error("map unavailable") },
        )
    }

    @Test
    fun `GPX fit includes every track point when no activity is ongoing`() {
        val track = GpxTrack(
            listOf(
                GpxLine(listOf(GpxPoint(41.0, 2.0), GpxPoint(42.0, 3.0))),
                GpxLine(listOf(GpxPoint(43.0, 4.0))),
            ),
        )
        val expectedInsets = CameraFitInsets(24, 32, 40, 48)
        var capturedBounds: LatLngBounds? = null
        var capturedInsets: CameraFitInsets? = null

        zoomOnGpxTrack(
            map = mockk(),
            track = track,
            isActivityOngoing = false,
            insets = expectedInsets,
            fitBounds = { _, bounds, insets ->
                capturedBounds = bounds
                capturedInsets = insets
            },
        )

        assertEquals(expectedInsets, capturedInsets)
        track.lines.flatMap(GpxLine::points).forEach { point ->
            assertTrue(
                capturedBounds!!.contains(
                    org.maplibre.android.geometry.LatLng(point.latitude, point.longitude),
                ),
            )
        }
    }

    @Test
    fun `GPX import does not move the camera during an ongoing activity`() {
        var fitCalls = 0
        zoomOnGpxTrack(
            map = mockk(),
            track = GpxTrack(listOf(GpxLine(listOf(GpxPoint(41.0, 2.0), GpxPoint(42.0, 3.0))))),
            isActivityOngoing = true,
            insets = CameraFitInsets(0, 0, 0, 0),
            fitBounds = { _, _, _ -> fitCalls++ },
        )

        assertEquals(0, fitCalls)
    }

    @Test
    fun `user zoom handles empty history and direction of travel`() {
        val map = mockk<MapLibreMap>(relaxed = true)
        val location = Location("test").apply {
            latitude = 41.0
            longitude = 2.0
            time = 1_000L
            bearing = 0f
        }

        zoomOnUser(map, location, recentLocations = emptyList(), isDirectionOfTravel = true)
        verify(exactly = 1) { map.animateCamera(any()) }
    }
}

private fun cat.itur.app.core.model.Location.toAndroidLocation() = Location("test").apply {
    latitude = this@toAndroidLocation.latitude
    longitude = this@toAndroidLocation.longitude
}
