/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map.ui.components.map

import cat.itur.app.core.model.GpxLine
import cat.itur.app.core.model.GpxPoint
import cat.itur.app.core.model.GpxTrack
import org.maplibre.geojson.LineString
import kotlin.test.Test
import kotlin.test.assertEquals

class GpxOverlayTest {
    @Test
    fun `overlay is absent without a track and emits one line feature per drawable segment`() {
        assertEquals(0, GpxOverlay.featuresFor(null).size)
        val track = GpxTrack(
            listOf(
                GpxLine(listOf(GpxPoint(1.0, 2.0), GpxPoint(3.0, 4.0))),
                GpxLine(listOf(GpxPoint(5.0, 6.0))),
            ),
        )
        assertEquals(1, GpxOverlay.featuresFor(track).size)
    }

    @Test
    fun `overlay simplification keeps the line endpoints`() {
        val points = (0..5_000).map { GpxPoint(it.toDouble() / 100, it.toDouble() / 100) }
        val feature = GpxOverlay.featuresFor(GpxTrack(listOf(GpxLine(points)))).single()
        val coordinates = (feature.geometry() as LineString).coordinates()
        assertEquals(0.0, coordinates.first().latitude())
        assertEquals(50.0, coordinates.last().longitude())
    }
}
