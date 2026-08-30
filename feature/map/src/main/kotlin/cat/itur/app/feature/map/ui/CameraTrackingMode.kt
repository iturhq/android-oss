/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map.ui

/** The one camera target that should be continuously framed, if any. */
internal enum class CameraTrackingMode {
    NONE,
    USER,
    GROUP,
}

internal fun CameraTrackingMode.toggle(requested: CameraTrackingMode): CameraTrackingMode {
    require(requested != CameraTrackingMode.NONE)
    return if (this == requested) CameraTrackingMode.NONE else requested
}

internal fun CameraTrackingMode.stopForManualZoom(): CameraTrackingMode = CameraTrackingMode.NONE
