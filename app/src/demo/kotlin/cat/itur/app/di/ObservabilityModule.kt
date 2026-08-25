/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.di

import cat.itur.app.observability.NoOpObservabilityInitializer
import cat.itur.app.observability.ObservabilityInitializer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ObservabilityModule {

    @Binds
    abstract fun bindObservabilityInitializer(
        impl: NoOpObservabilityInitializer,
    ): ObservabilityInitializer
}
