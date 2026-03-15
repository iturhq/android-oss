/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.location

import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest

/**
 * Abstraction over location update subscriptions, allowing a controllable
 * fake to be substituted in tests instead of the real [FusedLocationProviderClient].
 */
interface LocationClient {
    fun requestUpdates(request: LocationRequest, callback: LocationCallback, looper: Looper)
    fun removeUpdates(callback: LocationCallback)
}
