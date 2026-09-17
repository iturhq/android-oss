/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map.ui

import androidx.compose.ui.geometry.Rect
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap

internal data class CameraFitInsets(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

/**
 * Keeps group bounds out from under the live bottom control lanes. Until layout has completed,
 * each side keeps the density-scaled fallback clearance instead.
 */
internal fun cameraFitInsets(
    mapBounds: Rect?,
    leftControlBounds: Rect?,
    rightControlBounds: Rect?,
    horizontalFallback: Int,
    verticalPadding: Int,
): CameraFitInsets {
    val left = if (mapBounds != null && leftControlBounds != null) {
        (leftControlBounds.right - mapBounds.left).toInt() + verticalPadding
    } else {
        horizontalFallback
    }
    val right = if (mapBounds != null && rightControlBounds != null) {
        (mapBounds.right - rightControlBounds.left).toInt() + verticalPadding
    } else {
        horizontalFallback
    }
    return CameraFitInsets(
        left = maxOf(horizontalFallback, left),
        top = verticalPadding,
        right = maxOf(horizontalFallback, right),
        bottom = verticalPadding,
    )
}

internal fun animateToBounds(
    map: MapLibreMap,
    bounds: LatLngBounds,
    insets: CameraFitInsets,
) {
    map.animateCamera(
        CameraUpdateFactory.newLatLngBounds(
            bounds,
            insets.left,
            insets.top,
            insets.right,
            insets.bottom,
        ),
    )
}
