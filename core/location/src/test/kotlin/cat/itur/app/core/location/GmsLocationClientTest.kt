/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.core.location

import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

class GmsLocationClientTest {

    private val fused = mockk<FusedLocationProviderClient>()
    private val callbackExecutor = Executor(Runnable::run)

    @Test
    fun productionCallbackExecutorRunsOffTheCallingThread() {
        val callingThread = Thread.currentThread()
        val executor = newLocationCallbackExecutor()

        try {
            val callbackThread = executor.submit<Thread> { Thread.currentThread() }
                .get(5, TimeUnit.SECONDS)

            assertNotEquals(callingThread, callbackThread)
            assertEquals(LOCATION_THREAD_NAME, callbackThread.name)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun requestUsesHighAccuracyCadenceAndDeliversLatestLocation() {
        val request = slot<LocationRequest>()
        val callback = slot<LocationCallback>()
        every {
            fused.requestLocationUpdates(capture(request), callbackExecutor, capture(callback))
        } returns mockk(relaxed = true)
        val client = GmsLocationClient(fused, callbackExecutor)
        val expected = mockk<Location>()
        var delivered: Location? = null

        client.requestUpdates(intervalMillis = 2_000L) { delivered = it }
        callback.captured.onLocationResult(
            mockk<LocationResult> {
                every { locations } returns listOf(mockk(), expected)
            },
        )

        assertEquals(2_000L, request.captured.intervalMillis)
        assertEquals(Priority.PRIORITY_HIGH_ACCURACY, request.captured.priority)
        assertSame(expected, delivered)
    }

    @Test
    fun removeUpdatesCancelsTheMatchingGmsCallback() {
        val callback = slot<LocationCallback>()
        every {
            fused.requestLocationUpdates(any<LocationRequest>(), callbackExecutor, capture(callback))
        } returns mockk(relaxed = true)
        every { fused.removeLocationUpdates(any<LocationCallback>()) } returns mockk(relaxed = true)
        val client = GmsLocationClient(fused, callbackExecutor)
        val consumer: (Location) -> Unit = {}

        client.requestUpdates(intervalMillis = 2_000L, onLocation = consumer)
        client.removeUpdates(consumer)

        verify(exactly = 1) { fused.removeLocationUpdates(callback.captured) }
    }

    @Test
    fun replacingTheSameConsumerKeepsOnlyOneProviderRequestActive() {
        val callbacks = mutableListOf<LocationCallback>()
        every {
            fused.requestLocationUpdates(
                any<LocationRequest>(),
                callbackExecutor,
                capture(callbacks),
            )
        } returns mockk(relaxed = true)
        every { fused.removeLocationUpdates(any<LocationCallback>()) } returns mockk(relaxed = true)
        val client = GmsLocationClient(fused, callbackExecutor)
        val consumer: (Location) -> Unit = {}

        client.requestUpdates(intervalMillis = 2_000L, onLocation = consumer)
        client.requestUpdates(intervalMillis = 2_000L, onLocation = consumer)

        verify(exactly = 2) {
            fused.requestLocationUpdates(
                any<LocationRequest>(),
                callbackExecutor,
                any<LocationCallback>(),
            )
        }
        verify(exactly = 1) { fused.removeLocationUpdates(callbacks.first()) }
    }
}
