/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.core.model

/**
 * A geographic position.
 */
data class Location(
    val latitude: Double,
    val longitude: Double,
    /** Epoch time supplied by the location provider, in milliseconds. */
    val providerTimestampMillis: Long? = null,
    val altitudeMeters: Double? = null,
    val speedMetersPerSecond: Float? = null,
    val bearingDegrees: Float? = null,
    val horizontalAccuracyMeters: Float? = null,
    val verticalAccuracyMeters: Float? = null,
    val speedAccuracyMetersPerSecond: Float? = null,
    val bearingAccuracyDegrees: Float? = null,
)
