/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.core.auth.health

import cat.itur.app.core.data.health.BackendDiagnosticEvidence
import cat.itur.app.core.data.health.BackendHealthObservation
import cat.itur.app.core.data.health.BackendHealthReporter
import cat.itur.app.core.data.health.BackendServiceIds
import cat.itur.app.core.data.repository.SignInFailureReason

internal fun BackendHealthReporter.reportFirebaseAuthSucceeded(detail: String) {
    runCatching {
        report(
            BackendServiceIds.FIREBASE_AUTH,
            BackendHealthObservation.OperationSucceeded(detail),
        )
    }
}

internal fun BackendHealthReporter.reportFirebaseAuthFailed(reason: SignInFailureReason) {
    if (reason == SignInFailureReason.NO_ACCOUNT) return
    val summary = when (reason) {
        SignInFailureReason.NO_ACCOUNT -> return
        SignInFailureReason.NOT_CONFIGURED -> "Google sign-in configuration is invalid"
        SignInFailureReason.SERVICE_UNAVAILABLE -> "Google sign-in service is unavailable"
        SignInFailureReason.UNEXPECTED -> "Google sign-in failed unexpectedly"
    }
    runCatching {
        report(
            BackendServiceIds.FIREBASE_AUTH,
            BackendHealthObservation.OperationFailed(
                BackendDiagnosticEvidence.sanitized(summary),
            ),
        )
    }
}
