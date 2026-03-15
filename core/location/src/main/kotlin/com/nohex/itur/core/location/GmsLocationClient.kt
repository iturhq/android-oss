/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.location

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices

/**
 * A [LocationClient] backed by [FusedLocationProviderClient].
 *
 * The underlying client is created lazily on first use so that the Play Services
 * broker connection is deferred until location is actually needed, preventing
 * spurious [SecurityException] log spam at app startup.
 *
 * Permission is checked by the caller before [requestUpdates] is invoked.
 */
class GmsLocationClient(
    private val context: Context,
) : LocationClient {

    private val fused by lazy { LocationServices.getFusedLocationProviderClient(context) }

    @SuppressLint("MissingPermission")
    override fun requestUpdates(
        request: LocationRequest,
        callback: LocationCallback,
        looper: Looper,
    ) {
        fused.requestLocationUpdates(request, callback, looper)
    }

    override fun removeUpdates(callback: LocationCallback) {
        fused.removeLocationUpdates(callback)
    }
}
