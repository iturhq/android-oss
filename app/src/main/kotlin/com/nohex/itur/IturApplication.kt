/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur

import android.app.Application
import android.os.StrictMode
import com.nohex.itur.observability.ObservabilityInitializer
import dagger.hilt.android.HiltAndroidApp
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer
import javax.inject.Inject

@HiltAndroidApp
class IturApplication : Application() {

    @Inject
    lateinit var observabilityInitializer: ObservabilityInitializer

    override fun onCreate() {
        super.onCreate()

        observabilityInitializer.initialize()

        // Initialise MapLibre.
        MapLibre.getInstance(
            this,
            BuildConfig.MAPLIBRE_API_KEY,
            WellKnownTileServer.MapLibre,
        )

        // Log misuse of the main thread during development.
        if (BuildConfig.DEBUG) {
            setupStrictMode()
        }
    }

    private fun setupStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .detectCustomSlowCalls()
                .penaltyLog()
                .build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .detectActivityLeaks()
                .detectLeakedRegistrationObjects()
                .penaltyLog()
                .build(),
        )
    }
}
