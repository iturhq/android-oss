/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.data.health

import kotlinx.coroutines.CancellationException

internal object NoOpBackendHealthReporter : BackendHealthReporter {
    override fun report(serviceId: String, observation: BackendHealthObservation) = Unit
}

/** Reports only the Firestore mutation boundary, never reads or application-owned values. */
@Suppress("TooGenericExceptionCaught")
internal suspend fun <T> BackendHealthReporter.observeFirestoreMutation(
    operation: suspend () -> T,
): T = try {
    operation().also {
        report(
            BackendServiceIds.FIREBASE_FIRESTORE,
            BackendHealthObservation.OperationSucceeded("Cloud Firestore mutation completed"),
        )
    }
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (failure: Exception) {
    report(
        BackendServiceIds.FIREBASE_FIRESTORE,
        BackendHealthObservation.OperationFailed(
            BackendDiagnosticEvidence.sanitized("Cloud Firestore mutation failed"),
        ),
    )
    throw failure
}
