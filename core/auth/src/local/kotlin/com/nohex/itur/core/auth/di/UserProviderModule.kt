/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.auth.di

import com.nohex.itur.core.auth.di.provider.DeviceUserProvider
import com.nohex.itur.core.auth.di.provider.FirebaseUserProvider
import com.nohex.itur.core.auth.di.provider.UserProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UserProviderModule {

    @Provides
    @Singleton
    fun provideUserProviders(
        firebaseUserProvider: FirebaseUserProvider,
        deviceUserProvider: DeviceUserProvider,
    ): List<UserProvider> = listOf(
        firebaseUserProvider,
        deviceUserProvider,
    )
}
