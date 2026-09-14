/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.core.data.repository

/** A monotonic, elapsed-time-only clock. Its values must never be persisted as wall time. */
fun interface MonotonicClock {
    fun nowNanos(): Long
}

object SystemMonotonicClock : MonotonicClock {
    override fun nowNanos(): Long = System.nanoTime()
}

enum class LocationReportStage {
    CALLBACK_QUEUE,
    NORMALIZATION,
    IDENTITY_PREPARATION,
    KEY_TOPIC_PREPARATION,
    ENCODING,
    ENCRYPTION,
    CONNECTION_WAIT,
    PUBLISH_SEND,
    PUBACK_WAIT,
}

enum class LocationReportOutcome {
    SUCCESS,
    FAILED,
    CANCELLED,
    SUPERSEDED,
}

/**
 * Identifier-free handle for one accepted GPS report. Implementations must be thread-safe and
 * make terminal transitions idempotent so instrumentation cannot change reporting semantics.
 */
interface LocationReportMeasurement {
    fun stageStarted(stage: LocationReportStage)

    fun stageFinished(stage: LocationReportStage)

    fun complete(outcome: LocationReportOutcome)

    companion object {
        val None: LocationReportMeasurement =
            object : LocationReportMeasurement {
                override fun stageStarted(stage: LocationReportStage) = Unit

                override fun stageFinished(stage: LocationReportStage) = Unit

                override fun complete(outcome: LocationReportOutcome) = Unit
            }
    }
}

data class LocationReportDurationAggregate(
    val count: Long = 0,
    val totalNanos: Long = 0,
    val minimumNanos: Long = 0,
    val maximumNanos: Long = 0,
)

/**
 * Process-local, constant-memory aggregate. It deliberately contains only durations and counts:
 * no coordinates, identifiers, topic/key/name/credential data, payloads, or wall timestamps.
 */
data class LocationReportMetricsSnapshot(
    val started: Long,
    val outcomes: Map<LocationReportOutcome, Long>,
    val currentlyInFlight: Long,
    val peakInFlight: Long,
    val total: LocationReportDurationAggregate,
    val stages: Map<LocationReportStage, LocationReportDurationAggregate>,
)

interface LocationReportMetrics {
    fun newReport(): LocationReportMeasurement

    fun snapshot(): LocationReportMetricsSnapshot

    companion object {
        val None: LocationReportMetrics =
            object : LocationReportMetrics {
                override fun newReport(): LocationReportMeasurement = LocationReportMeasurement.None

                override fun snapshot() = LocationReportMetricsSnapshot(
                    started = 0,
                    outcomes = LocationReportOutcome.entries.associateWith { 0L },
                    currentlyInFlight = 0,
                    peakInFlight = 0,
                    total = LocationReportDurationAggregate(),
                    stages =
                    LocationReportStage.entries.associateWith {
                        LocationReportDurationAggregate()
                    },
                )
            }
    }
}

/** Exact aggregation of every report with bounded memory and zero telemetry or persistence. */
class AggregatingLocationReportMetrics(
    private val clock: MonotonicClock = SystemMonotonicClock,
) : LocationReportMetrics {
    private val lock = Any()
    private var started = 0L
    private var inFlight = 0L
    private var peakInFlight = 0L
    private val outcomes = LocationReportOutcome.entries.associateWith { 0L }.toMutableMap()
    private var total = LocationReportDurationAggregate()
    private val stages =
        LocationReportStage.entries
            .associateWith { LocationReportDurationAggregate() }
            .toMutableMap()

    override fun newReport(): LocationReportMeasurement {
        val acceptedAt = clock.nowNanos()
        synchronized(lock) {
            started++
            inFlight++
            peakInFlight = maxOf(peakInFlight, inFlight)
        }
        return Measurement(acceptedAt)
    }

    override fun snapshot(): LocationReportMetricsSnapshot = synchronized(lock) {
        LocationReportMetricsSnapshot(
            started = started,
            outcomes = outcomes.toMap(),
            currentlyInFlight = inFlight,
            peakInFlight = peakInFlight,
            total = total,
            stages = stages.toMap(),
        )
    }

    private inner class Measurement(
        private val acceptedAt: Long,
    ) : LocationReportMeasurement {
        private val measurementLock = Any()
        private val stageStarts = LongArray(LocationReportStage.entries.size) { UNSET }
        private val stageDurations = LongArray(LocationReportStage.entries.size)
        private val stageSeen = BooleanArray(LocationReportStage.entries.size)
        private var completed = false

        override fun stageStarted(stage: LocationReportStage) = synchronized(measurementLock) {
            if (!completed && stageStarts[stage.ordinal] == UNSET) {
                stageSeen[stage.ordinal] = true
                stageStarts[stage.ordinal] = clock.nowNanos()
            }
        }

        override fun stageFinished(stage: LocationReportStage) = synchronized(measurementLock) {
            val index = stage.ordinal
            val stageStart = stageStarts[index]
            if (!completed && stageStart != UNSET) {
                stageDurations[index] += elapsed(stageStart, clock.nowNanos())
                stageStarts[index] = UNSET
            }
        }

        override fun complete(outcome: LocationReportOutcome) {
            val finishedAt = clock.nowNanos()
            val durations =
                synchronized(measurementLock) {
                    if (completed) return
                    completed = true
                    stageStarts.indices.forEach { index ->
                        val stageStart = stageStarts[index]
                        if (stageStart != UNSET) {
                            stageDurations[index] += elapsed(stageStart, finishedAt)
                        }
                    }
                    stageDurations.copyOf() to stageSeen.copyOf()
                }
            synchronized(lock) {
                inFlight--
                outcomes[outcome] = outcomes.getValue(outcome) + 1
                total = total.add(elapsed(acceptedAt, finishedAt))
                durations.first.forEachIndexed { index, duration ->
                    if (durations.second[index]) {
                        val stage = LocationReportStage.entries[index]
                        stages[stage] = stages.getValue(stage).add(duration)
                    }
                }
            }
        }
    }

    private fun LocationReportDurationAggregate.add(duration: Long) = copy(
        count = count + 1,
        totalNanos = totalNanos + duration,
        minimumNanos = if (count == 0L) duration else minOf(minimumNanos, duration),
        maximumNanos = if (count == 0L) duration else maxOf(maximumNanos, duration),
    )

    private fun elapsed(start: Long, end: Long): Long = maxOf(0L, end - start)

    private companion object {
        const val UNSET = Long.MIN_VALUE
    }
}
