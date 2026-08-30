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

internal fun pointerBearingRotation(
    location: Location,
    nowMillis: Long = System.currentTimeMillis(),
): Float? {
    val bearing = location.bearingDegrees ?: return null
    if (!bearing.isFinite() || bearing !in 0f..360f) return null
    val timestamp = location.providerTimestampMillis ?: return null
    val age = nowMillis - timestamp
    if (age !in 0..MAX_POINTER_BEARING_AGE_MILLIS) return null
    val accuracy = location.bearingAccuracyDegrees
    if (accuracy != null &&
        (!accuracy.isFinite() || accuracy < 0f || accuracy > MAX_POINTER_BEARING_ACCURACY_DEGREES)
    ) {
        return null
    }
    return bearing % 360f
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
