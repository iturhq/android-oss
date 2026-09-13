/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.core.ui.components

internal data class LoadingArcGeometry(
    val startAngle: Float,
    val sweepAngle: Float,
)

internal fun loadingArcGeometry(sweepProgress: Float): LoadingArcGeometry = LoadingArcGeometry(
    startAngle = LOADING_ARC_START_ANGLE,
    sweepAngle =
    LOADING_ARC_MIN_SWEEP +
        (LOADING_ARC_MAX_SWEEP - LOADING_ARC_MIN_SWEEP) * sweepProgress.coerceIn(0f, 1f),
)

private const val LOADING_ARC_START_ANGLE = -90f
private const val LOADING_ARC_MIN_SWEEP = 40f
private const val LOADING_ARC_MAX_SWEEP = 300f
