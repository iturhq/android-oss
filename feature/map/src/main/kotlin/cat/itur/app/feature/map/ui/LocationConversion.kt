/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map.ui

import android.annotation.SuppressLint
import android.os.Build
import android.location.Location as AndroidLocation
import cat.itur.app.core.model.Location as IturLocation

/** Preserve only values the Android provider explicitly supplied. */
@SuppressLint("NewApi") // Every API-26 accessor is guarded by sdkInt for deterministic tests.
internal fun AndroidLocation.toIturLocation(
    sdkInt: Int = Build.VERSION.SDK_INT,
): IturLocation = IturLocation(
    latitude = latitude,
    longitude = longitude,
    providerTimestampMillis = time.takeIf { it > 0L },
    altitudeMeters = if (hasAltitude()) altitude else null,
    speedMetersPerSecond = if (hasSpeed()) speed else null,
    bearingDegrees = if (hasBearing()) bearing else null,
    horizontalAccuracyMeters = if (hasAccuracy()) accuracy else null,
    verticalAccuracyMeters = if (sdkInt >= Build.VERSION_CODES.O && hasVerticalAccuracy()) {
        verticalAccuracyMeters
    } else {
        null
    },
    speedAccuracyMetersPerSecond = if (sdkInt >= Build.VERSION_CODES.O && hasSpeedAccuracy()) {
        speedAccuracyMetersPerSecond
    } else {
        null
    },
    bearingAccuracyDegrees = if (sdkInt >= Build.VERSION_CODES.O && hasBearingAccuracy()) {
        bearingAccuracyDegrees
    } else {
        null
    },
)
