/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map.health

import cat.itur.app.core.data.health.BackendHealthStatus
import cat.itur.app.feature.map.config.MapStyleConfig
import dagger.Lazy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class MapStyleHealthIntegrationTest {

    @Test
    fun `renderer failure degrades registered map style and retries until renderer recovery`() = runTest {
        var endpointProbes = 0
        val check = MapStyleBackendHealthCheck(
            MapStyleConfig("https://example.test/style.json?key=test"),
        ) { endpointProbes++ }
        val coordinator = BackendHealthRecoveryCoordinator(setOf(check))
        val renderer = MapStyleRendererHealthReporter(Lazy { coordinator })

        coordinator.start(this)
        runCurrent()
        assertEquals(1, endpointProbes)
        assertEquals(BackendHealthStatus.WORKING, coordinator.services.value.single().status)

        renderer.styleLoadFailed()
        runCurrent()
        assertEquals(BackendHealthStatus.DEGRADED, coordinator.services.value.single().status)
        assertEquals(5, coordinator.retryCountdown.value)

        advanceTimeBy(5_001L)
        runCurrent()
        assertEquals(2, endpointProbes)
        assertEquals(BackendHealthStatus.DEGRADED, coordinator.services.value.single().status)
        assertEquals(10, coordinator.retryCountdown.value)

        renderer.styleLoadSucceeded()
        runCurrent()
        assertEquals(BackendHealthStatus.WORKING, coordinator.services.value.single().status)
        assertNull(coordinator.retryCountdown.value)
        coordinator.stop()
    }

    @Test
    fun `endpoint failure is unavailable rather than renderer degradation`() = runTest {
        val check = MapStyleBackendHealthCheck(MapStyleConfig("https://example.test/style.json")) {
            error("Endpoint unavailable")
        }
        val coordinator = BackendHealthRecoveryCoordinator(setOf(check))

        coordinator.refresh()

        assertEquals(BackendHealthStatus.UNAVAILABLE, coordinator.services.value.single().status)
    }
}
