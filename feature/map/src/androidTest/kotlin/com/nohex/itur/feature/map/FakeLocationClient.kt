/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.feature.map

import android.location.Location
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.nohex.itur.core.location.LocationClient

/**
 * A controllable [LocationClient] for instrumented tests.
 *
 * Call [emit] to push a [Location] to the registered callback synchronously,
 * simulating a GPS fix without requiring real hardware or a looper.
 */
class FakeLocationClient : LocationClient {

    private var activeCallback: LocationCallback? = null

    override fun requestUpdates(
        request: LocationRequest,
        callback: LocationCallback,
        looper: Looper,
    ) {
        activeCallback = callback
    }

    override fun removeUpdates(callback: LocationCallback) {
        if (activeCallback === callback) activeCallback = null
    }

    /** Push a [location] to the registered callback immediately. */
    fun emit(location: Location) {
        activeCallback?.onLocationResult(LocationResult.create(listOf(location)))
    }

    /** Push a location at the given [latitude]/[longitude]. */
    fun emit(latitude: Double, longitude: Double) {
        emit(
            Location("fake").also {
                it.latitude = latitude
                it.longitude = longitude
            },
        )
    }
}
