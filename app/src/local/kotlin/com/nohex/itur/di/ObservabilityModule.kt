/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.di

import com.nohex.itur.observability.FirebaseObservabilityInitializer
import com.nohex.itur.observability.ObservabilityInitializer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ObservabilityModule {

    @Binds
    abstract fun bindObservabilityInitializer(
        impl: FirebaseObservabilityInitializer,
    ): ObservabilityInitializer
}
