/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map

import cat.itur.app.core.location.LocationClient
import cat.itur.app.core.location.di.LocationModule
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/**
 * Replaces [LocationModule] during instrumented tests with a [FakeLocationClient]
 * so tests can push deterministic locations without GPS hardware.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [LocationModule::class])
object TestLocationModule {

    @Provides
    @Singleton
    fun provideFakeLocationClient(): FakeLocationClient = FakeLocationClient()

    @Provides
    @Singleton
    fun provideLocationClient(client: FakeLocationClient): LocationClient = client
}
