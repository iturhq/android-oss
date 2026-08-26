/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map.ui

import android.location.Location
import io.mockk.mockk
import org.junit.Test
import org.maplibre.android.maps.MapLibreMap
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InitialLocationCenteringTest {

    private val map = mockk<MapLibreMap>()
    private val location = mockk<Location>()

    @Test
    fun `camera waits until both map and first location are ready`() {
        assertFalse(centerOnInitialLocationIfReady(map, null, alreadyCentered = false))
        assertFalse(centerOnInitialLocationIfReady(null, location, alreadyCentered = false))
    }

    @Test
    fun `first ready location centers camera exactly once`() {
        var centerCount = 0
        val center: (MapLibreMap, Location) -> Unit = { _, _ -> centerCount++ }

        val centered = centerOnInitialLocationIfReady(
            map = map,
            location = location,
            alreadyCentered = false,
            center = center,
        )
        val stillCentered = centerOnInitialLocationIfReady(
            map = map,
            location = location,
            alreadyCentered = centered,
            center = center,
        )

        assertTrue(stillCentered)
        assertTrue(centerCount == 1)
    }
}
