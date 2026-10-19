/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.core.model

/** A locally loaded GPX track. Each line is a GPX track segment or route. */
data class GpxTrack(val lines: List<GpxLine>)

data class GpxLine(val points: List<GpxPoint>)

data class GpxPoint(val latitude: Double, val longitude: Double)
