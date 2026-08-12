/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.feature.map.health

import com.nohex.itur.core.data.health.BackendDiagnosticEvidence
import com.nohex.itur.core.data.health.BackendHealthCheck
import com.nohex.itur.core.data.health.BackendHealthObservation
import com.nohex.itur.core.data.health.BackendHealthStatus
import com.nohex.itur.core.data.health.BackendService
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class BackendHealthPrerequisiteTest {

    @Test
    fun `unavailable Auth blocks Firestore without inventing Firestore evidence then recovery probes it`() = runTest {
        var authAvailable = false
        var firestoreProbes = 0
        val auth = check("firebase-auth", "Firebase Authentication") {
            if (!authAvailable) error("Auth unavailable password=private")
        }
        val firestore = check(
            id = "firebase-firestore",
            name = "Cloud Firestore",
            prerequisites = setOf("firebase-auth"),
        ) { firestoreProbes++ }
        val coordinator = BackendHealthRecoveryCoordinator(setOf(firestore, auth))

        coordinator.refresh()

        val blocked = coordinator.services.value.single { it.id == "firebase-firestore" }
        assertEquals(0, firestoreProbes)
        assertEquals(BackendHealthStatus.UNAVAILABLE, blocked.status)
        assertEquals("Blocked by unavailable prerequisite: Firebase Authentication", blocked.detail)
        assertNull(blocked.diagnosticTrace)

        authAvailable = true
        coordinator.refresh()

        assertEquals(1, firestoreProbes)
        assertEquals(
            listOf(BackendHealthStatus.WORKING, BackendHealthStatus.WORKING),
            coordinator.services.value.map { it.status },
        )
    }

    @Test
    fun `degraded Auth does not block Firestore and reachability cannot erase degradation`() = runTest {
        var firestoreProbes = 0
        val auth = check("firebase-auth", "Firebase Authentication") {}
        val firestore = check(
            id = "firebase-firestore",
            name = "Cloud Firestore",
            prerequisites = setOf("firebase-auth"),
        ) { firestoreProbes++ }
        val coordinator = BackendHealthRecoveryCoordinator(setOf(auth, firestore))
        coordinator.refresh()
        firestoreProbes = 0
        coordinator.report(
            "firebase-auth",
            BackendHealthObservation.OperationFailed(
                BackendDiagnosticEvidence.sanitized("Google sign-in rejected"),
            ),
        )

        coordinator.refresh()

        assertEquals(1, firestoreProbes)
        assertEquals(
            BackendHealthStatus.DEGRADED,
            coordinator.services.value.single { it.id == "firebase-auth" }.status,
        )
        assertEquals(
            BackendHealthStatus.WORKING,
            coordinator.services.value.single { it.id == "firebase-firestore" }.status,
        )
    }

    @Test
    fun `unknown and cyclic prerequisites fail before any probe`() {
        assertFailsWith<IllegalArgumentException> {
            BackendHealthRecoveryCoordinator(
                setOf(check("firestore", "Firestore", setOf("missing")) {}),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            BackendHealthRecoveryCoordinator(
                setOf(
                    check("auth", "Auth", setOf("firestore")) {},
                    check("firestore", "Firestore", setOf("auth")) {},
                ),
            )
        }
    }

    private fun check(
        id: String,
        name: String,
        prerequisites: Set<String> = emptySet(),
        probe: suspend () -> Unit,
    ) = object : BackendHealthCheck {
        override val service = BackendService(id, name)
        override val prerequisiteServiceIds = prerequisites
        override suspend fun probe() = probe()
    }
}
