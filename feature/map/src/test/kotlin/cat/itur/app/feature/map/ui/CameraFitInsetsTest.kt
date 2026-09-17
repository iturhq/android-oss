/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map.ui

import androidx.compose.ui.geometry.Rect
import cat.itur.app.core.data.TestFixtures
import io.mockk.mockk
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
}
