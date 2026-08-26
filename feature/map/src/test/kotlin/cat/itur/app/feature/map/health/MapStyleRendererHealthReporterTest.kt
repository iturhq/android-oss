/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map.health

import cat.itur.app.core.data.health.BackendHealthObservation
import cat.itur.app.core.data.health.BackendHealthReporter
import cat.itur.app.core.data.health.BackendServiceIds
import dagger.Lazy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MapStyleRendererHealthReporterTest {

    @Test
    fun `renderer load failure reports fixed sanitised degraded map-style evidence`() {
        val reporter = RecordingReporter()
        val healthReporter = MapStyleRendererHealthReporter(Lazy { reporter })

        healthReporter.styleLoadFailed()

        val (serviceId, observation) = reporter.reports.single()
        assertEquals(BackendServiceIds.MAP_STYLE, serviceId)
        val failure = assertIs<BackendHealthObservation.OperationFailed>(observation)
        assertEquals("Map style renderer failed to load", failure.evidence.summary)
        assertNull(failure.evidence.diagnosticTrace)
        assertTrue("http" !in failure.evidence.summary.lowercase())
        assertTrue("token" !in failure.evidence.summary.lowercase())
    }

    @Test
    fun `successful renderer load reports immediate map-style recovery`() {
        val reporter = RecordingReporter()
        val healthReporter = MapStyleRendererHealthReporter(Lazy { reporter })

        healthReporter.styleLoadFailed()
        healthReporter.styleLoadSucceeded()

        assertIs<BackendHealthObservation.OperationFailed>(reporter.reports.first().second)
        val recovery = assertIs<BackendHealthObservation.OperationSucceeded>(reporter.reports.last().second)
        assertEquals("Map style rendered", recovery.detail)
    }

    @Test
    fun `repeated frame callbacks do not flood operation health`() {
        val reporter = RecordingReporter()
        val healthReporter = MapStyleRendererHealthReporter(Lazy { reporter })

        healthReporter.styleLoadFailed()
        healthReporter.styleLoadFailed()
        healthReporter.styleLoadSucceeded()
        healthReporter.styleLoadSucceeded()

        assertEquals(2, reporter.reports.size)
    }
}

private class RecordingReporter : BackendHealthReporter {
    val reports = mutableListOf<Pair<String, BackendHealthObservation>>()

    override fun report(serviceId: String, observation: BackendHealthObservation) {
        reports += serviceId to observation
    }
}
