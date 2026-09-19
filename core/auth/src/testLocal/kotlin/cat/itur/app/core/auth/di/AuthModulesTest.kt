/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.core.auth.di

import android.content.Context
import cat.itur.app.core.auth.config.GoogleSignInConfig
import cat.itur.app.core.auth.health.FirebaseAuthBackendHealthCheck
import cat.itur.app.core.auth.repository.FirebaseUserRepository
import cat.itur.app.core.data.health.BackendHealthReporter
import com.google.firebase.auth.FirebaseAuth
import dagger.Lazy
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertSame

class AuthModulesTest {
    @AfterTest
    fun tearDown() {
        unmockkStatic(FirebaseAuth::class)
    }

    @Test
    fun `GIVEN local configuration WHEN providing Firebase Auth THEN returns emulator-configured instance`() {
        mockkStatic(FirebaseAuth::class)
        val auth = mockk<FirebaseAuth>(relaxed = true)
        every { FirebaseAuth.getInstance() } returns auth

        assertSame(auth, FirebaseAuthModule.provideFirebaseAuth())
    }

    @Test
    fun `WHEN providing auth health check and user repository THEN preserves the supplied instances`() {
        val healthCheck = mockk<FirebaseAuthBackendHealthCheck>()
        val repository = FirebaseUserRepository(
            mockk(),
            mockk<Context>(relaxed = true),
            GoogleSignInConfig("client"),
            Lazy { mockk<BackendHealthReporter>() },
        )

        assertSame(healthCheck, FirebaseAuthModule.provideFirebaseAuthHealthCheck(healthCheck))
        assertSame(repository, UserModule.provideUserRepository(repository))
    }
}
