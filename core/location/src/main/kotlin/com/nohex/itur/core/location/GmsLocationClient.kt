/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * A [LocationClient] backed by [FusedLocationProviderClient].
 *
 * The underlying client is created lazily on first use so that the Play Services
 * broker connection is deferred until location is actually needed, preventing
 * spurious [SecurityException] log spam at app startup.
 *
 * Permission is checked by the caller before [requestUpdates] is invoked.
 */
class GmsLocationClient private constructor(
    fusedProvider: () -> FusedLocationProviderClient,
    looperProvider: () -> Looper,
) : LocationClient {

    constructor(context: Context) : this(
        fusedProvider = { LocationServices.getFusedLocationProviderClient(context) },
        looperProvider = { Looper.getMainLooper() },
    )

    internal constructor(
        fused: FusedLocationProviderClient,
        looper: Looper,
    ) : this(
        fusedProvider = { fused },
        looperProvider = { looper },
    )

    private val fused by lazy(fusedProvider)
    private val looper by lazy(looperProvider)
    private val callbacks = mutableMapOf<(Location) -> Unit, LocationCallback>()

    @SuppressLint("MissingPermission")
    override fun requestUpdates(
        intervalMillis: Long,
        onLocation: (Location) -> Unit,
    ) {
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.lastOrNull()?.let(onLocation)
            }
        }
        callbacks.put(onLocation, callback)?.let(fused::removeLocationUpdates)
        val request =
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis).build()
        fused.requestLocationUpdates(request, callback, looper)
    }

    override fun removeUpdates(onLocation: (Location) -> Unit) {
        callbacks.remove(onLocation)?.let(fused::removeLocationUpdates)
    }
}
