/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map.ui.components.map

import androidx.annotation.DrawableRes
import cat.itur.app.core.model.ParticipantSignal
import cat.itur.app.feature.map.R

internal enum class PointerRole { ORGANIZER, OTHER }

internal data class PointerPresentation(
    @DrawableRes val drawable: Int,
    val imageName: String,
)

internal fun pointerPresentation(
    role: PointerRole,
    signal: ParticipantSignal?,
    directional: Boolean,
): PointerPresentation {
    val status = when (signal) {
        ParticipantSignal.DELAYED -> "delayed"
        ParticipantSignal.NEEDS_HELP -> "needs-help"
        null -> "okay"
    }
    val drawable = if (directional) {
        directionalPointerDrawable(role, signal)
    } else {
        neutralPointerDrawable(role, signal)
    }
    val direction = if (directional) "directional" else "neutral"
    return PointerPresentation(drawable, "marker-${role.name.lowercase()}-$status-$direction")
}

@DrawableRes
private fun directionalPointerDrawable(role: PointerRole, signal: ParticipantSignal?): Int = when (role to signal) {
    PointerRole.ORGANIZER to ParticipantSignal.DELAYED ->
        R.drawable.ic_location_organiser_delayed
    PointerRole.ORGANIZER to ParticipantSignal.NEEDS_HELP ->
        R.drawable.ic_location_organiser_needs_help
    PointerRole.ORGANIZER to null -> R.drawable.ic_location_organiser
    PointerRole.OTHER to ParticipantSignal.DELAYED ->
        R.drawable.ic_location_other_delayed
    PointerRole.OTHER to ParticipantSignal.NEEDS_HELP ->
        R.drawable.ic_location_other_needs_help
    PointerRole.OTHER to null -> R.drawable.ic_location_other
    else -> error("Unsupported pointer role and signal")
}

@DrawableRes
private fun neutralPointerDrawable(role: PointerRole, signal: ParticipantSignal?): Int = when (role to signal) {
    PointerRole.ORGANIZER to ParticipantSignal.DELAYED ->
        R.drawable.ic_location_organiser_neutral_delayed
    PointerRole.ORGANIZER to ParticipantSignal.NEEDS_HELP ->
        R.drawable.ic_location_organiser_neutral_needs_help
    PointerRole.ORGANIZER to null -> R.drawable.ic_location_organiser_neutral
    PointerRole.OTHER to ParticipantSignal.DELAYED ->
        R.drawable.ic_location_other_neutral_delayed
    PointerRole.OTHER to ParticipantSignal.NEEDS_HELP ->
        R.drawable.ic_location_other_neutral_needs_help
    PointerRole.OTHER to null -> R.drawable.ic_location_other_neutral
    else -> error("Unsupported pointer role and signal")
}
