/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.data.health

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackendHealthCheckTest {

    private val service = BackendService("test", "Test service")
    private val working = BackendServiceHealth(
        id = service.id,
        name = service.displayName,
        status = BackendHealthStatus.WORKING,
        detail = "Reachable",
    )

    @Test
    fun `health aggregation uses unavailable then degraded then working precedence`() {
        assertEquals(
            BackendHealthStatus.UNAVAILABLE,
            aggregateBackendHealthStatus(
                listOf(
                    BackendHealthStatus.WORKING,
                    BackendHealthStatus.UNAVAILABLE,
                    BackendHealthStatus.DEGRADED,
                ),
            ),
        )
        assertEquals(
            BackendHealthStatus.DEGRADED,
            aggregateBackendHealthStatus(
                listOf(BackendHealthStatus.WORKING, BackendHealthStatus.DEGRADED),
            ),
        )
        assertEquals(BackendHealthStatus.WORKING, aggregateBackendHealthStatus(emptyList()))
    }

    @Test
    fun `reachability success cannot erase operation degradation`() {
        val degraded = working.withObservation(
            BackendHealthObservation.OperationFailed(
                BackendDiagnosticEvidence.sanitized("Publish rejected"),
            ),
        )

        val stillDegraded = degraded.withObservation(
            BackendHealthObservation.ReachabilitySucceeded("Connection established"),
        )

        assertEquals(BackendHealthStatus.DEGRADED, stillDegraded.status)
        assertEquals("Publish rejected", stillDegraded.detail)
    }

    @Test
    fun `successful operation restores degraded service immediately`() {
        val degraded = working.withObservation(
            BackendHealthObservation.OperationFailed(
                BackendDiagnosticEvidence.sanitized("Write rejected"),
            ),
        )

        val recovered = degraded.withObservation(
            BackendHealthObservation.OperationSucceeded("Write completed"),
        )

        assertEquals(BackendHealthStatus.WORKING, recovered.status)
        assertEquals("Write completed", recovered.detail)
        assertTrue(recovered.available)
    }

    @Test
    fun `unavailable remains more severe than a subsequent operation failure`() {
        val unavailable = working.withObservation(
            BackendHealthObservation.ReachabilityFailed(
                BackendDiagnosticEvidence.sanitized("Connection refused"),
            ),
        )

        val result = unavailable.withObservation(
            BackendHealthObservation.OperationFailed(
                BackendDiagnosticEvidence.sanitized("Publish rejected"),
            ),
        )

        assertEquals(BackendHealthStatus.UNAVAILABLE, result.status)
        assertEquals("Connection refused", result.detail)
        assertFalse(result.available)
    }

    @Test
    fun `diagnostic evidence redacts credentials URLs and tokens`() {
        val jwt = "aaaaaaaaaa.bbbbbbbbbb.cccccccccc"
        val evidence = BackendDiagnosticEvidence.from(
            IllegalStateException(
                "GET https://example.test/path?token=query-secret " +
                    "Authorization: Bearer header-secret password=plain $jwt",
            ),
        )

        assertFalse(evidence.summary.contains("query-secret"))
        assertFalse(evidence.summary.contains("header-secret"))
        assertFalse(evidence.summary.contains("plain"))
        assertFalse(evidence.summary.contains(jwt))
        assertFalse(evidence.diagnosticTrace.orEmpty().contains("query-secret"))
    }

    @Test
    fun `legacy two-state health checks still implement the unchanged probe contract`() {
        val legacy = object : BackendHealthCheck {
            override val service = this@BackendHealthCheckTest.service

            override suspend fun probe() = Unit
        }

        assertEquals(service, legacy.service)
    }
}
