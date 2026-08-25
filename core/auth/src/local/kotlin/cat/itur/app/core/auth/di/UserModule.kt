/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.core.auth.di

import cat.itur.app.core.auth.repository.FirebaseUserRepository
import cat.itur.app.core.data.repository.UserRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object UserModule {

    @Provides
    fun provideUserRepository(
        impl: FirebaseUserRepository,
    ): UserRepository = impl
}
