/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.feature.map.di

import com.nohex.itur.core.data.health.BackendHealthCheck
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

/**
 * Declares an empty-capable extension set. OSS Firebase modules and proprietary consumers can
 * contribute checks with `@IntoSet` without feature code knowing about their implementations.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BackendHealthModule {
    @Multibinds
    abstract fun backendHealthChecks(): Set<BackendHealthCheck>
}
