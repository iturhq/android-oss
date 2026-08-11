/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.feature.map.health

import android.util.Log
import com.nohex.itur.core.data.health.BackendDiagnosticEvidence
import com.nohex.itur.core.data.health.BackendHealthCheck
import com.nohex.itur.core.data.health.BackendHealthObservation
import com.nohex.itur.core.data.health.BackendHealthStatus
import com.nohex.itur.core.data.health.BackendServiceHealth
import com.nohex.itur.core.data.health.withObservation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

/** One lifecycle-owned source of backend health and recovery timing for feature and host UI. */
@Singleton
class BackendHealthRecoveryCoordinator @Inject constructor(
    checks: Set<@JvmSuppressWildcards BackendHealthCheck>,
) {
    private val orderedChecks = checks
        .sortedWith(compareBy(BackendHealthCheck::diagnosticOrder, { it.service.id }))
        .also { sorted ->
            require(sorted.map { it.service.id }.distinct().size == sorted.size) {
                "Backend health service identifiers must be unique"
            }
        }
    private val checksById = orderedChecks.associateBy { it.service.id }
    private val mutableServices = MutableStateFlow(
        orderedChecks.map { check ->
            BackendServiceHealth(
                id = check.service.id,
                name = check.service.displayName,
                status = BackendHealthStatus.UNAVAILABLE,
                detail = "Not checked yet",
            )
        },
    )
    private val mutableRetryCountdown = MutableStateFlow<Int?>(null)
    private val mutableCheckGeneration = MutableStateFlow(0L)

    val services: StateFlow<List<BackendServiceHealth>> = mutableServices.asStateFlow()
    val retryCountdown: StateFlow<Int?> = mutableRetryCountdown.asStateFlow()
    val checkGeneration: StateFlow<Long> = mutableCheckGeneration.asStateFlow()

    private var monitoringScope: CoroutineScope? = null
    private var scheduledJob: Job? = null
    private var scheduledMode: ScheduledMode? = null
    private var scheduleGeneration = 0L
    private var checkJob: Job? = null
    private var retryAttempt = 0

    /** Starts or resumes monitoring once. Repeated starts for the same scope are no-ops. */
    fun start(scope: CoroutineScope) {
        if (monitoringScope === scope && (checkJob?.isActive == true || scheduledJob?.isActive == true)) {
            return
        }
        if (monitoringScope !== scope) {
            stop()
            monitoringScope = scope
        }
        when {
            mutableCheckGeneration.value == 0L -> requestHealthCheck()
            hasFailures() -> ensureRetryScheduled()
            else -> ensureCadenceScheduled()
        }
    }

    /** Stops countdown, cadence, and probes while retaining the last service evidence. */
    fun stop() {
        monitoringScope = null
        scheduleGeneration += 1
        scheduledJob?.cancel()
        scheduledJob = null
        scheduledMode = null
        checkJob?.cancel()
        checkJob = null
        mutableRetryCountdown.value = null
    }

    /** Performs the compatibility startup check without starting background scheduling. */
    fun checkOnce(scope: CoroutineScope) {
        if (mutableCheckGeneration.value > 0L || checkJob?.isActive == true) return
        checkJob = scope.launch {
            try {
                performHealthCheck()
            } finally {
                checkJob = null
            }
        }
    }

    /** Probes immediately and restarts any subsequent failure backoff at five seconds. */
    fun retryNow(fallbackScope: CoroutineScope? = null) {
        retryAttempt = 0
        requestHealthCheck(fallbackScope)
    }

    /** Rechecks after an automatic operation signal without weakening accumulated backoff. */
    fun recheckNow(fallbackScope: CoroutineScope? = null) {
        requestHealthCheck(fallbackScope)
    }

    /** Suspended manual refresh for host diagnostics; genuine caller cancellation propagates. */
    suspend fun refresh() {
        retryAttempt = 0
        scheduleGeneration += 1
        scheduledJob?.cancel()
        scheduledJob = null
        scheduledMode = null
        checkJob?.cancel()
        checkJob = null
        mutableRetryCountdown.value = null
        performHealthCheck()
    }

    /** Applies stronger real-operation evidence and wakes the single recovery schedule. */
    fun report(
        serviceId: String,
        observation: BackendHealthObservation,
    ) {
        if (serviceId !in checksById) return
        mutableServices.update { current ->
            current.map { service ->
                if (service.id == serviceId) service.withObservation(observation) else service
            }
        }
        scheduleForCurrentState()
    }

    /** Maps a thrown operation failure onto the smallest recognizing service set. */
    fun reportFailure(
        cause: Throwable,
        assumeAllServices: Boolean = false,
        fallbackScope: CoroutineScope? = null,
    ): Boolean {
        val recognized = orderedChecks.filter { it.recognizes(cause) }
        val failedChecks = when {
            recognized.isNotEmpty() -> recognized
            assumeAllServices -> orderedChecks
            else -> {
                requestHealthCheck(fallbackScope)
                return false
            }
        }
        val evidence = BackendDiagnosticEvidence.from(cause)
        failedChecks.forEach { check ->
            report(
                check.service.id,
                BackendHealthObservation.ReachabilityFailed(evidence),
            )
        }
        return failedChecks.isNotEmpty()
    }

    private fun requestHealthCheck(fallbackScope: CoroutineScope? = null) {
        scheduleGeneration += 1
        scheduledJob?.cancel()
        scheduledJob = null
        scheduledMode = null
        checkJob?.cancel()
        mutableRetryCountdown.value = null
        val scope = monitoringScope ?: fallbackScope ?: return
        checkJob = scope.launch {
            try {
                performHealthCheck()
            } finally {
                checkJob = null
            }
        }
    }

    private suspend fun performHealthCheck() {
        val observations = coroutineScope {
            orderedChecks.map { check ->
                async { check.service.id to check.probeObservation() }
            }.awaitAll()
        }
        observations.forEach { (serviceId, observation) ->
            mutableServices.update { current ->
                current.map { service ->
                    if (service.id == serviceId) service.withObservation(observation) else service
                }
            }
        }
        mutableCheckGeneration.value += 1
        scheduleForCurrentState()
    }

    private suspend fun BackendHealthCheck.probeObservation(): BackendHealthObservation = try {
        withTimeout(BACKEND_PROBE_TIMEOUT_MILLIS) {
            probe()
            BackendHealthObservation.ReachabilitySucceeded(successDetail)
        }
    } catch (timeout: TimeoutCancellationException) {
        Log.w("BackendHealth", "Probe timed out for ${service.id}", timeout)
        BackendHealthObservation.ReachabilityFailed(
            BackendDiagnosticEvidence.from(
                timeout,
                fallbackMessage = "Timed out after ${BACKEND_PROBE_TIMEOUT_MILLIS / 1_000} seconds",
            ),
        )
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        Log.w("BackendHealth", "Probe failed for ${service.id}: ${failure.message}", failure)
        BackendHealthObservation.ReachabilityFailed(BackendDiagnosticEvidence.from(failure))
    }

    private fun scheduleForCurrentState() {
        if (hasFailures()) {
            ensureRetryScheduled()
        } else {
            retryAttempt = 0
            ensureCadenceScheduled()
        }
    }

    private fun ensureRetryScheduled() {
        val scope = monitoringScope ?: return
        if (scheduledMode == ScheduledMode.RETRY && scheduledJob?.isActive == true) return
        scheduledJob?.cancel()
        scheduledMode = ScheduledMode.RETRY
        val generation = ++scheduleGeneration
        val delaySeconds = RETRY_DELAYS_SECONDS.getOrElse(retryAttempt) {
            RETRY_DELAYS_SECONDS.last()
        }
        scheduledJob = scope.launch {
            try {
                for (remaining in delaySeconds downTo 1) {
                    mutableRetryCountdown.value = remaining
                    delay(1_000L)
                }
                retryAttempt += 1
                if (scheduleGeneration == generation) {
                    scheduledJob = null
                    scheduledMode = null
                    mutableRetryCountdown.value = null
                }
                requestHealthCheck()
            } finally {
                if (scheduleGeneration == generation) mutableRetryCountdown.value = null
            }
        }
    }

    private fun ensureCadenceScheduled() {
        val scope = monitoringScope ?: return
        if (scheduledMode == ScheduledMode.CADENCE && scheduledJob?.isActive == true) return
        scheduledJob?.cancel()
        mutableRetryCountdown.value = null
        scheduledMode = ScheduledMode.CADENCE
        scheduleGeneration += 1
        scheduledJob = scope.launch {
            delay(BACKEND_MONITOR_INTERVAL_MILLIS)
            scheduledJob = null
            scheduledMode = null
            requestHealthCheck()
        }
    }

    private fun hasFailures(): Boolean = mutableServices.value.any {
        it.status != BackendHealthStatus.WORKING
    }

    private enum class ScheduledMode { RETRY, CADENCE }

    companion object {
        val RETRY_DELAYS_SECONDS = listOf(5, 10, 20, 40, 60)
        const val BACKEND_PROBE_TIMEOUT_MILLIS = 5_000L
        const val BACKEND_MONITOR_INTERVAL_MILLIS = 30_000L
    }
}
