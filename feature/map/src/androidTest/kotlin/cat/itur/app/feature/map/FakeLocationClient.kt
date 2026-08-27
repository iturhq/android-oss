/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map

import android.location.Location
import cat.itur.app.core.location.LocationClient
import java.util.concurrent.atomic.AtomicInteger

/**
 * A controllable [LocationClient] for instrumented tests.
 *
 * Call [emit] to push a [Location] to the registered callback synchronously,
 * simulating a GPS fix without requiring real hardware or a looper.
 */
class FakeLocationClient : LocationClient {

    private var activeCallback: ((Location) -> Unit)? = null
    val requestCount = AtomicInteger()
    val removeCount = AtomicInteger()
    val hasActiveRequest: Boolean
        get() = activeCallback != null

    override fun requestUpdates(
        intervalMillis: Long,
        onLocation: (Location) -> Unit,
    ) {
        requestCount.incrementAndGet()
        activeCallback = onLocation
    }

    override fun removeUpdates(onLocation: (Location) -> Unit) {
        if (activeCallback === onLocation) activeCallback = null
        removeCount.incrementAndGet()
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
