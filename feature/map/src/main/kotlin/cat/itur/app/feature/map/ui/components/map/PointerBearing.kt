/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map.ui.components.map

import cat.itur.app.core.model.Location
import cat.itur.app.core.model.ParticipantLocation
import cat.itur.app.core.model.ParticipantSignal
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point

internal const val MAX_POINTER_BEARING_AGE_MILLIS = 30_000L
internal const val MAX_POINTER_BEARING_ACCURACY_DEGREES = 45f
private const val MIN_POINTER_BEARING_DEGREES = 0f
private const val FULL_TURN_DEGREES = 360f

internal fun pointerBearingRotation(
    location: Location,
    nowMillis: Long = System.currentTimeMillis(),
): Float? = location.bearingDegrees
    ?.takeIf { it.isFinite() && it in MIN_POINTER_BEARING_DEGREES..FULL_TURN_DEGREES }
    ?.takeIf { location.hasFreshBearing(nowMillis) }
    ?.takeIf { location.bearingAccuracyDegrees.isReliableBearingAccuracy() }
    ?.rem(FULL_TURN_DEGREES)

private fun Location.hasFreshBearing(nowMillis: Long): Boolean {
    val timestamp = providerTimestampMillis ?: return false
    return nowMillis - timestamp in 0..MAX_POINTER_BEARING_AGE_MILLIS
}

private fun Float?.isReliableBearingAccuracy(): Boolean = this == null || isFiniteAndWithinBearingAccuracyRange()

private fun Float.isFiniteAndWithinBearingAccuracyRange(): Boolean {
    val validRange = MIN_POINTER_BEARING_DEGREES..MAX_POINTER_BEARING_ACCURACY_DEGREES
    return isFinite() && this in validRange
}

internal fun remotePointerFeature(
    participantLocation: ParticipantLocation,
    role: PointerRole,
    signal: ParticipantSignal?,
    opacity: Float,
    nowMillis: Long = System.currentTimeMillis(),
): Feature {
    val bearing = pointerBearingRotation(participantLocation.location, nowMillis)
    return Feature.fromGeometry(
        Point.fromLngLat(
            participantLocation.location.longitude,
            participantLocation.location.latitude,
        ),
    ).apply {
        addStringProperty("label", participantLocation.userName)
        addStringProperty("id", participantLocation.userId.value)
        bearing?.let { addNumberProperty("bearing", it) }
        addNumberProperty("opacity", opacity)
        addStringProperty(
            "marker",
            pointerPresentation(role, signal, directional = bearing != null).imageName,
        )
    }
}
