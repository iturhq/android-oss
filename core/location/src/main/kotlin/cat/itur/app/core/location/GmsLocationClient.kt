/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.core.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

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
    callbackExecutorProvider: () -> Executor,
) : LocationClient {

    constructor(context: Context) : this(
        fusedProvider = { LocationServices.getFusedLocationProviderClient(context) },
        callbackExecutorProvider = ::newLocationCallbackExecutor,
    )

    internal constructor(
        fused: FusedLocationProviderClient,
        callbackExecutor: Executor,
    ) : this(
        fusedProvider = { fused },
        callbackExecutorProvider = { callbackExecutor },
    )

    private val fused by lazy(fusedProvider)
    private val callbackExecutor by lazy(callbackExecutorProvider)
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
        fused.requestLocationUpdates(request, callbackExecutor, callback)
    }

    override fun removeUpdates(onLocation: (Location) -> Unit) {
        callbacks.remove(onLocation)?.let(fused::removeLocationUpdates)
    }
}

internal const val LOCATION_THREAD_NAME = "itur-location"

internal fun newLocationCallbackExecutor(): ExecutorService = Executors.newSingleThreadExecutor { task ->
    Thread(task, LOCATION_THREAD_NAME).apply {
        priority = Thread.NORM_PRIORITY
    }
}
