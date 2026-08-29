/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.core.auth.health

import cat.itur.app.core.data.health.BackendHealthObservation
import cat.itur.app.core.data.health.BackendHealthReporter
import cat.itur.app.core.data.health.BackendServiceIds
import cat.itur.app.core.data.repository.SignInFailureReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FirebaseAuthOperationHealthTest {

    @Test
    fun `rejected Google sign-in reports fixed sanitised Firebase Auth evidence`() {
        val reporter = RecordingReporter()

        reporter.reportFirebaseAuthFailed(SignInFailureReason.SERVICE_UNAVAILABLE)

        val (serviceId, observation) = reporter.reports.single()
        assertEquals(BackendServiceIds.FIREBASE_AUTH, serviceId)
        val failure = assertIs<BackendHealthObservation.OperationFailed>(observation)
        assertEquals("Google sign-in service is unavailable", failure.evidence.summary)
        assertEquals(null, failure.evidence.diagnosticTrace)
        assertTrue("token" !in failure.evidence.summary)
        assertTrue("credential" !in failure.evidence.summary)
    }

    @Test
    fun `successful Auth operation reports immediate recovery evidence`() {
        val reporter = RecordingReporter()

        reporter.reportFirebaseAuthFailed(SignInFailureReason.UNEXPECTED)
        reporter.reportFirebaseAuthSucceeded("Google sign-in completed")

        assertIs<BackendHealthObservation.OperationFailed>(reporter.reports.first().second)
        val recovery = assertIs<BackendHealthObservation.OperationSucceeded>(reporter.reports.last().second)
        assertEquals("Google sign-in completed", recovery.detail)
    }

    @Test
    fun `no Google account is a user condition and is not reported as Auth degradation`() {
        val reporter = RecordingReporter()

        reporter.reportFirebaseAuthFailed(SignInFailureReason.NO_ACCOUNT)

        assertTrue(reporter.reports.isEmpty())
    }
}

private class RecordingReporter : BackendHealthReporter {
    val reports = mutableListOf<Pair<String, BackendHealthObservation>>()

    override fun report(serviceId: String, observation: BackendHealthObservation) {
        reports += serviceId to observation
    }
}
