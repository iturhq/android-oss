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
    val drawable = when (Triple(role, signal, directional)) {
        Triple(PointerRole.ORGANIZER, ParticipantSignal.DELAYED, true) ->
            R.drawable.ic_location_organiser_delayed
        Triple(PointerRole.ORGANIZER, ParticipantSignal.NEEDS_HELP, true) ->
            R.drawable.ic_location_organiser_needs_help
        Triple(PointerRole.ORGANIZER, null, true) -> R.drawable.ic_location_organiser
        Triple(PointerRole.OTHER, ParticipantSignal.DELAYED, true) ->
            R.drawable.ic_location_other_delayed
        Triple(PointerRole.OTHER, ParticipantSignal.NEEDS_HELP, true) ->
            R.drawable.ic_location_other_needs_help
        Triple(PointerRole.OTHER, null, true) -> R.drawable.ic_location_other
        Triple(PointerRole.ORGANIZER, ParticipantSignal.DELAYED, false) ->
            R.drawable.ic_location_organiser_neutral_delayed
        Triple(PointerRole.ORGANIZER, ParticipantSignal.NEEDS_HELP, false) ->
            R.drawable.ic_location_organiser_neutral_needs_help
        Triple(PointerRole.ORGANIZER, null, false) -> R.drawable.ic_location_organiser_neutral
        Triple(PointerRole.OTHER, ParticipantSignal.DELAYED, false) ->
            R.drawable.ic_location_other_neutral_delayed
        Triple(PointerRole.OTHER, ParticipantSignal.NEEDS_HELP, false) ->
            R.drawable.ic_location_other_neutral_needs_help
        Triple(PointerRole.OTHER, null, false) -> R.drawable.ic_location_other_neutral
        else -> error("Unsupported pointer role and signal")
    }
    val direction = if (directional) "directional" else "neutral"
    return PointerPresentation(drawable, "marker-${role.name.lowercase()}-$status-$direction")
}
