/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.core.auth.di

import cat.itur.app.core.auth.health.FirebaseAuthBackendHealthCheck
import cat.itur.app.core.data.health.BackendHealthCheck
import com.google.firebase.auth.FirebaseAuth
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
object FirebaseAuthModule {

    // 10.0.2.2 is the loopback alias that routes to the host machine from an Android emulator.
    private const val EMULATOR_HOST = "10.0.2.2"
    private const val AUTH_EMULATOR_PORT = 9099

    @Provides
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance().also {
        it.useEmulator(EMULATOR_HOST, AUTH_EMULATOR_PORT)
    }

    @Provides
    @IntoSet
    fun provideFirebaseAuthHealthCheck(
        healthCheck: FirebaseAuthBackendHealthCheck,
    ): BackendHealthCheck = healthCheck
}
