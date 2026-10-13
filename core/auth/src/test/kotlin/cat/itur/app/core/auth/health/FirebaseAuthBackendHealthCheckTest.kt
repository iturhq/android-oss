/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.core.auth.health

import cat.itur.app.core.data.health.BackendServiceIds
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GetTokenResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FirebaseAuthBackendHealthCheckTest {
    private val firebaseAuth = mockk<FirebaseAuth>(relaxed = true)
    private val healthCheck = FirebaseAuthBackendHealthCheck(firebaseAuth)

    @Test
    fun `GIVEN no authenticated session WHEN probing THEN succeeds without creating a user`() = runBlocking {
        every { firebaseAuth.currentUser } returns null

        healthCheck.probe()

        verify(exactly = 0) { firebaseAuth.signInWithCredential(any()) }
    }

    @Test
    fun `GIVEN an authenticated session WHEN probing THEN refreshes its ID token`() = runBlocking {
        val user = mockk<FirebaseUser>()
        every { firebaseAuth.currentUser } returns user
        every { user.getIdToken(true) } returns Tasks.forResult(mockk<GetTokenResult>())

        healthCheck.probe()

        verify(exactly = 1) { user.getIdToken(true) }
    }

    @Test
    fun `GIVEN a nested Firebase Auth exception WHEN recognizing a failure THEN identifies the auth backend`() {
        val firebaseFailure = FirebaseAuthException("ERROR_NETWORK_REQUEST_FAILED", "offline")
        val wrappedFailure = IllegalStateException("outer", RuntimeException("inner", firebaseFailure))

        assertTrue(healthCheck.recognizes(wrappedFailure))
        assertFalse(healthCheck.recognizes(IllegalArgumentException("not Firebase")))
    }

    @Test
    fun `WHEN exposing health metadata THEN uses the stable Firebase Auth service contract`() {
        assertEquals(BackendServiceIds.FIREBASE_AUTH, healthCheck.service.id)
        assertEquals("Firebase Authentication", healthCheck.service.displayName)
        assertEquals(30, healthCheck.diagnosticOrder)
        assertEquals("Authentication session reachable", healthCheck.successDetail)
    }
}
