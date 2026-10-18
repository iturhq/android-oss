/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map.ui.components.map

import androidx.annotation.DrawableRes
import cat.itur.app.core.model.ParticipantSignal
import cat.itur.app.feature.map.R

internal enum class PointerRole { SELF, ORGANIZER, OTHER }

internal data class PointerPresentation(
    @DrawableRes val drawable: Int,
    val imageName: String,
    val statusLabel: String,
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
    return PointerPresentation(
        drawable = drawable,
        imageName = "marker-${role.name.lowercase()}-$status-$direction",
        statusLabel = status,
    )
}

@DrawableRes
private fun directionalPointerDrawable(role: PointerRole, signal: ParticipantSignal?): Int = when (role to signal) {
    PointerRole.SELF to ParticipantSignal.DELAYED -> R.drawable.ic_location_self_delayed
    PointerRole.SELF to ParticipantSignal.NEEDS_HELP -> R.drawable.ic_location_self_needs_help
    PointerRole.SELF to null -> R.drawable.ic_location_self
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
    PointerRole.SELF to ParticipantSignal.DELAYED -> R.drawable.ic_location_self_delayed
    PointerRole.SELF to ParticipantSignal.NEEDS_HELP -> R.drawable.ic_location_self_needs_help
    PointerRole.SELF to null -> R.drawable.ic_location_self
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

internal fun MapLibreViewInput.accessibilityDescription(
    nowMillis: Long,
    thresholds: LocationRecencyThresholds = LocationRecencyThresholds(),
): String = participantLocations.joinToString("; ", prefix = "Participant locations. ") { location ->
    val role = when (location.userId) {
        currentUserId -> "self"
        organizerId -> "organiser"
        else -> "participant"
    }
    val state = pointerPresentation(
        PointerRole.OTHER,
        participantSignals[location.userId],
        directional = true,
    ).statusLabel
    "${location.userName}, $role, $state, ${location.accessibleAge(nowMillis, thresholds)}"
}
