/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.di

import com.nohex.itur.BuildConfig
import com.nohex.itur.core.auth.config.GoogleSignInConfig
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
}
