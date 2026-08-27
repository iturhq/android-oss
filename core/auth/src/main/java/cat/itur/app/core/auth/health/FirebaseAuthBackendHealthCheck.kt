/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.core.auth.health

import cat.itur.app.core.data.health.BackendHealthCheck
import cat.itur.app.core.data.health.BackendService
import cat.itur.app.core.data.health.BackendServiceIds
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Uses Firebase Authentication's native token endpoint when a session exists.
 *
 * With no current session there is no side-effect-free Auth request: creating or signing in a
 * user would violate the health-check contract. Authentication operation failures are still
 * reported immediately through [recognizes].
 */
class FirebaseAuthBackendHealthCheck @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
) : BackendHealthCheck {
    private companion object {
        const val DIAGNOSTIC_ORDER = 30
    }

    override val service = BackendService(
        id = BackendServiceIds.FIREBASE_AUTH,
        displayName = "Firebase Authentication",
    )
    override val diagnosticOrder = DIAGNOSTIC_ORDER
    override val successDetail = "Authentication session reachable"

    override suspend fun probe() {
        firebaseAuth.currentUser?.getIdToken(true)?.await()
    }

    override fun recognizes(cause: Throwable): Boolean = generateSequence(cause) { it.cause }
        .any { it is FirebaseAuthException }
}
