/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.auth.di

import com.nohex.itur.core.auth.di.provider.DeviceUserIdProvider
import com.nohex.itur.core.auth.di.provider.FirebaseUserIdProvider
import com.nohex.itur.core.auth.di.provider.UserIdProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UserIdProviderModule {

    @Provides
    @Singleton
    fun provideUserIdProviders(
        firebaseUserIdProvider: FirebaseUserIdProvider,
        deviceUserIdProvider: DeviceUserIdProvider,
    ): List<UserIdProvider> = listOf(
        firebaseUserIdProvider,
        deviceUserIdProvider,
    )
}
