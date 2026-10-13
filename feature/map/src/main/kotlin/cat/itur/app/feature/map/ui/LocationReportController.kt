/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map.ui

import android.location.Location
import android.util.Log
import cat.itur.app.core.data.repository.LocationReportMeasurement
import cat.itur.app.core.data.repository.LocationReportOutcome
import cat.itur.app.core.data.repository.LocationReportStage
import cat.itur.app.core.data.repository.LocationRepository
import cat.itur.app.core.domain.id.IturActivityId
import cat.itur.app.core.domain.model.User
import cat.itur.app.core.location.LocationClient
import cat.itur.app.feature.map.config.LocationUpdateConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

internal class LocationReportController(
    private val dependencies: LocationReportDependencies,
    private val callbacks: LocationReportCallbacks,
) {
    private val lock = Any()
    private var generation = 0L
    private var nextReportSequence = 0L
    private var latestReportSequence = -1L
    private var writer: Job? = null
    private var pendingReport: QueuedLocationReport? = null

    @Volatile
    var isActive = false
        private set

    private val locationCallback: (Location) -> Unit = locationCallback@{ location ->
        callbacks.onLocationReceived(location)
        val activityId = callbacks.ongoingActivityId() ?: return@locationCallback
        val participant = callbacks.currentUser() ?: return@locationCallback
        val (reportGeneration, reportSequence) = synchronized(lock) {
            generation to nextReportSequence++
        }
        val measurement = dependencies.locationsRepository.newReportMeasurement()
        measurement.stageStarted(LocationReportStage.CALLBACK_QUEUE)
        measurement.stageFinished(LocationReportStage.CALLBACK_QUEUE)
        val normalized = try {
            measurement.stageStarted(LocationReportStage.NORMALIZATION)
            location.toIturLocation()
        } catch (_: Exception) {
            measurement.complete(LocationReportOutcome.FAILED)
            return@locationCallback
        } finally {
            measurement.stageFinished(LocationReportStage.NORMALIZATION)
        }
        enqueue(
            QueuedLocationReport(
                participant.id,
                activityId,
                normalized,
                measurement,
                reportGeneration,
                reportSequence,
            ),
        )
    }

    @Suppress("TooGenericExceptionCaught")
    fun start() {
        synchronized(lock) {
            if (isActive) return
            isActive = true
        }
        Log.d("MapScreen", "Requesting location updates")
        try {
            dependencies.locationClient.requestUpdates(
                dependencies.locationUpdateConfig.updateIntervalMillis,
                locationCallback,
            )
        } catch (failure: Exception) {
            synchronized(lock) { isActive = false }
            invalidate()
            throw failure
        }
        Log.d("MapScreen", "Location updates requested successfully")
    }

    fun stop() {
        synchronized(lock) {
            if (!isActive) return
            isActive = false
        }
        invalidate()
        dependencies.locationClient.removeUpdates(locationCallback)
    }

    fun enqueue(report: QueuedLocationReport) {
        synchronized(lock) {
            if (!isActive || report.generation != generation) {
                report.measurement.complete(LocationReportOutcome.CANCELLED)
            } else if (report.sequence < latestReportSequence) {
                report.measurement.complete(LocationReportOutcome.SUPERSEDED)
            } else if (writer == null) {
                latestReportSequence = report.sequence
                val newWriter = dependencies.scope.launch(
                    Dispatchers.IO,
                    start = CoroutineStart.LAZY,
                ) {
                    drain(report, report.generation)
                }
                writer = newWriter
                newWriter.start()
            } else {
                latestReportSequence = report.sequence
                pendingReport?.measurement?.complete(LocationReportOutcome.SUPERSEDED)
                pendingReport = report
            }
        }
    }

    fun invalidate() = synchronized(lock) {
        generation++
        pendingReport?.measurement?.complete(LocationReportOutcome.CANCELLED)
        pendingReport = null
        writer?.cancel()
        writer = null
    }

    suspend fun invalidateAndJoin() {
        val activeWriter = synchronized(lock) {
            generation++
            pendingReport?.measurement?.complete(LocationReportOutcome.CANCELLED)
            pendingReport = null
            writer.also { writer = null }
        }
        activeWriter?.cancelAndJoin()
    }

    private suspend fun drain(first: QueuedLocationReport, writerGeneration: Long) {
        val activeWriter = currentCoroutineContext().job
        var report = first
        try {
            while (true) {
                callbacks.writeReport(report)
                val nextReport = synchronized(lock) {
                    if (writerGeneration != generation) return@synchronized null
                    pendingReport?.also { pendingReport = null } ?: run {
                        if (writer === activeWriter) {
                            writer = null
                            callbacks.onWriterReleased()
                        }
                        null
                    }
                }
                report = nextReport ?: return
            }
        } finally {
            synchronized(lock) {
                if (writerGeneration == generation && writer === activeWriter) writer = null
            }
        }
    }
}

internal data class LocationReportDependencies(
    val locationsRepository: LocationRepository,
    val locationClient: LocationClient,
    val locationUpdateConfig: LocationUpdateConfig,
    val scope: CoroutineScope,
)

internal data class LocationReportCallbacks(
    val ongoingActivityId: () -> IturActivityId?,
    val currentUser: () -> User?,
    val onLocationReceived: (Location) -> Unit,
    val writeReport: suspend (QueuedLocationReport) -> Unit,
    val onWriterReleased: () -> Unit = {},
)

internal data class QueuedLocationReport(
    val userId: cat.itur.app.core.domain.id.UserId,
    val activityId: IturActivityId,
    val location: cat.itur.app.core.model.Location,
    val measurement: LocationReportMeasurement,
    val generation: Long,
    val sequence: Long,
)
