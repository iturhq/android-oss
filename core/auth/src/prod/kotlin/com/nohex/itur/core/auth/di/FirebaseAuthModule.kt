/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.auth.di

import com.google.firebase.auth.FirebaseAuth
import com.nohex.itur.core.auth.health.FirebaseAuthBackendHealthCheck
import com.nohex.itur.core.data.health.BackendHealthCheck
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
object FirebaseAuthModule {

    @Provides
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @IntoSet
    fun provideFirebaseAuthHealthCheck(
        healthCheck: FirebaseAuthBackendHealthCheck,
    ): BackendHealthCheck = healthCheck
}
