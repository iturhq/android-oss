/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.di

import com.nohex.itur.BuildConfig
import com.nohex.itur.core.auth.config.GoogleSignInConfig
import com.nohex.itur.feature.map.config.LocationUpdateConfig
import com.nohex.itur.feature.map.config.MapStyleConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Application-owned configuration for reusable modules that deliberately do not embed it
 * themselves (map style/tile-provider key, Google Sign-In web client ID, ...). Values come from
 * this app's own `local.properties`, via [BuildConfig].
 */
@Module
@InstallIn(SingletonComponent::class)
object AppConfigModule {

    @Provides
    fun provideMapStyleConfig(): MapStyleConfig = MapStyleConfig(
        styleUrl = "https://api.maptiler.com/maps/streets/style.json?key=${BuildConfig.MAPTILER_API_KEY}",
    )

    @Provides
    fun provideGoogleSignInConfig(): GoogleSignInConfig = GoogleSignInConfig(
        webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID,
    )

    /**
     * 2 seconds: the interval this app used before it became configurable. Unlike the other
     * values in this module, this one isn't a per-deployment secret or brand value -- it's a
     * battery-versus-precision tuning knob a consuming application may want to change outright.
     */
    @Provides
    fun provideLocationUpdateConfig(): LocationUpdateConfig = LocationUpdateConfig(
        updateIntervalMillis = 2_000L,
    )
}
