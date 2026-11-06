/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map.ui

import cat.itur.app.core.model.GpxTrack
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap

/** Fits a newly loaded local track only while the map is not serving an active activity. */
internal fun zoomOnGpxTrack(
    map: MapLibreMap,
    track: GpxTrack?,
    isActivityOngoing: Boolean,
    insets: CameraFitInsets,
    fitBounds: (MapLibreMap, LatLngBounds, CameraFitInsets) -> Unit = ::animateToBounds,
) {
    if (track == null || isActivityOngoing) return
    val points = track.lines.flatMap { line ->
        line.points.map { point -> LatLng(point.latitude, point.longitude) }
    }
    when {
        points.isEmpty() -> Unit
        points.size == 1 -> map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(points.single(), SINGLE_LOCATION_ZOOM),
        )
        else -> fitBounds(map, LatLngBounds.Builder().includes(points).build(), insets)
    }
}
