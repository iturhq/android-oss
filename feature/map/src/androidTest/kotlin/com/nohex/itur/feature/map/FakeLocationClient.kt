/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.feature.map

import android.location.Location
import com.nohex.itur.core.location.LocationClient

/**
 * A controllable [LocationClient] for instrumented tests.
 *
 * Call [emit] to push a [Location] to the registered callback synchronously,
 * simulating a GPS fix without requiring real hardware or a looper.
 */
class FakeLocationClient : LocationClient {

    private var activeCallback: ((Location) -> Unit)? = null
    val hasActiveRequest: Boolean
        get() = activeCallback != null

    override fun requestUpdates(
        intervalMillis: Long,
        onLocation: (Location) -> Unit,
    ) {
        activeCallback = onLocation
    }

    override fun removeUpdates(onLocation: (Location) -> Unit) {
        if (activeCallback === onLocation) activeCallback = null
    }

    /** Push a [location] to the registered callback immediately. */
    fun emit(location: Location) {
        activeCallback?.invoke(location)
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
