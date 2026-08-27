/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map

import cat.itur.app.feature.map.config.LocationUpdateConfig
import cat.itur.app.feature.map.config.MapStyleConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * [MapViewModel][cat.itur.app.feature.map.ui.MapViewModel] takes [MapStyleConfig] and
 * [LocationUpdateConfig] as application-owned configuration, normally supplied by the consuming
 * app's own `AppConfigModule` (see `app/.../di/AppConfigModule.kt`) -- which isn't reachable from
 * `feature:map`'s own instrumented tests, since `:app` isn't one of this module's dependencies.
 * Provides placeholder values so [MapScreenTest]'s Hilt graph has something to inject; the actual
 * values don't affect the behavior under test.
 */
@Module
@InstallIn(SingletonComponent::class)
object TestConfigModule {

    @Provides
    fun provideMapStyleConfig(): MapStyleConfig = MapStyleConfig(styleUrl = "https://example.invalid/style.json")

    @Provides
    fun provideLocationUpdateConfig(): LocationUpdateConfig = LocationUpdateConfig(updateIntervalMillis = 2_000L)
}
