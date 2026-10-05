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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch

internal class LocationReportController(
    private val dependencies: LocationReportDependencies,
    private val callbacks: LocationReportCallbacks,
) {
    private val lock = Any()
    private var generation = 0L
    private var writer: Job? = null
    private var pendingReport: QueuedLocationReport? = null

    var isActive = false
        private set

    private val locationCallback: (Location) -> Unit = locationCallback@{ location ->
        callbacks.onLocationReceived(location)
        val activityId = callbacks.ongoingActivityId() ?: return@locationCallback
        val participant = callbacks.currentUser() ?: return@locationCallback
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
                generation,
            ),
        )
    }

    fun start() {
        if (isActive) return
        Log.d("MapScreen", "Requesting location updates")
        dependencies.locationClient.requestUpdates(
            dependencies.locationUpdateConfig.updateIntervalMillis,
            locationCallback,
        )
        isActive = true
        Log.d("MapScreen", "Location updates requested successfully")
    }

    fun stop() {
        if (!isActive) return
        invalidate()
        dependencies.locationClient.removeUpdates(locationCallback)
        isActive = false
    }

    fun enqueue(report: QueuedLocationReport) {
        synchronized(lock) {
            if (!isActive || report.generation != generation) {
                report.measurement.complete(LocationReportOutcome.CANCELLED)
            } else if (writer == null) {
                writer = dependencies.scope.launch(Dispatchers.IO) {
                    drain(report, generation)
                }
            } else {
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
        var report = first
        try {
            while (true) {
                callbacks.writeReport(report)
                report = synchronized(lock) {
                    if (writerGeneration != generation) return
                    pendingReport?.also { pendingReport = null } ?: return
                }
            }
        } finally {
            synchronized(lock) {
                if (writerGeneration == generation) writer = null
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
)

internal data class QueuedLocationReport(
    val userId: cat.itur.app.core.domain.id.UserId,
    val activityId: IturActivityId,
    val location: cat.itur.app.core.model.Location,
    val measurement: LocationReportMeasurement,
    val generation: Long,
)
