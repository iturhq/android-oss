/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map.health

import cat.itur.app.core.data.health.BackendDiagnosticEvidence
import cat.itur.app.core.data.health.BackendHealthObservation
import cat.itur.app.core.data.health.BackendHealthReporter
import cat.itur.app.core.data.health.BackendServiceIds
import dagger.Lazy
import javax.inject.Inject

/** Converts MapLibre renderer callbacks into stable map-style operation health. */
class MapStyleRendererHealthReporter @Inject constructor(
    private val backendHealthReporter: Lazy<BackendHealthReporter>,
) {
    private var lastOutcome: RendererOutcome? = null

    fun styleLoadFailed() {
        reportIfChanged(
            RendererOutcome.FAILED,
            BackendHealthObservation.OperationFailed(
                BackendDiagnosticEvidence.sanitized("Map style renderer failed to load"),
            ),
        )
    }

    fun styleLoadSucceeded() {
        reportIfChanged(
            RendererOutcome.SUCCEEDED,
            BackendHealthObservation.OperationSucceeded("Map style rendered"),
        )
    }

    @Synchronized
    private fun reportIfChanged(
        outcome: RendererOutcome,
        observation: BackendHealthObservation,
    ) {
        if (lastOutcome == outcome) return
        runCatching {
            backendHealthReporter.get().report(BackendServiceIds.MAP_STYLE, observation)
        }.onSuccess {
            lastOutcome = outcome
        }
    }

    private enum class RendererOutcome { SUCCEEDED, FAILED }
}
