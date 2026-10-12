/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map.ui

import cat.itur.app.core.data.TestFixtures
import cat.itur.app.core.data.repository.LocationReportMeasurement
import cat.itur.app.core.data.repository.LocationReportOutcome
import cat.itur.app.core.data.repository.LocationReportStage
import cat.itur.app.core.data.repository.LocationRepository
import cat.itur.app.core.location.LocationClient
import cat.itur.app.core.model.Location
import cat.itur.app.feature.map.config.LocationUpdateConfig
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals

class LocationReportControllerTest {

    @Test
    fun `GIVEN a fix arrives as its writer retires THEN a new writer drains it`() = runTest {
        val writtenLongitudes = ConcurrentLinkedQueue<Double>()
        val releaseCount = AtomicInteger()
        val firstMeasurement = Measurement()
        val latestMeasurement = Measurement()
        val latestReport = report(longitude = 2.18, sequence = 1L, measurement = latestMeasurement)
        lateinit var controller: LocationReportController
        controller = LocationReportController(
            dependencies = LocationReportDependencies(
                locationsRepository = mockk<LocationRepository>(relaxed = true),
                locationClient = mockk<LocationClient>(relaxed = true),
                locationUpdateConfig = LocationUpdateConfig(updateIntervalMillis = 1_000L),
                scope = this,
            ),
            callbacks = LocationReportCallbacks(
                ongoingActivityId = { TestFixtures.ONGOING_ACTIVITY_ID },
                currentUser = { null },
                onLocationReceived = {},
                writeReport = { queued ->
                    writtenLongitudes += queued.location.longitude
                    queued.measurement.complete(LocationReportOutcome.SUCCESS)
                },
                onWriterReleased = {
                    if (releaseCount.getAndIncrement() == 0) controller.enqueue(latestReport)
                },
            ),
        )
        controller.start()

        controller.enqueue(report(longitude = 2.16, sequence = 0L, measurement = firstMeasurement))
        withTimeout(5_000L) { latestMeasurement.completed.await() }

        assertEquals(listOf(2.16, 2.18), writtenLongitudes.toList())
        assertEquals(LocationReportOutcome.SUCCESS, firstMeasurement.outcome)
        assertEquals(LocationReportOutcome.SUCCESS, latestMeasurement.outcome)
    }

    private fun report(
        longitude: Double,
        sequence: Long,
        measurement: Measurement,
    ) = QueuedLocationReport(
        userId = TestFixtures.PARTICIPANT_1_ID,
        activityId = TestFixtures.ONGOING_ACTIVITY_ID,
        location = Location(latitude = 41.0, longitude = longitude),
        measurement = measurement,
        generation = 0L,
        sequence = sequence,
    )

    private class Measurement : LocationReportMeasurement {
        val completed = CompletableDeferred<Unit>()
        var outcome: LocationReportOutcome? = null
            private set

        override fun stageStarted(stage: LocationReportStage) = Unit

        override fun stageFinished(stage: LocationReportStage) = Unit

        override fun complete(outcome: LocationReportOutcome) {
            this.outcome = outcome
            completed.complete(Unit)
        }
    }
}
