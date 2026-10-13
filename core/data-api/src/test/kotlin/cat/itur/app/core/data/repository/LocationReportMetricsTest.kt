/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.core.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class LocationReportMetricsTest {
    @Test
    fun `aggregates exact stage totals outcomes overlap and idempotent terminals`() {
        val clock = FakeClock()
        val metrics = AggregatingLocationReportMetrics(clock)
        val first = metrics.newReport()
        clock.advance(2)
        first.stageStarted(LocationReportStage.CALLBACK_QUEUE)
        clock.advance(3)
        val second = metrics.newReport()
        first.stageFinished(LocationReportStage.CALLBACK_QUEUE)
        first.stageStarted(LocationReportStage.NORMALIZATION)
        clock.advance(5)
        first.stageFinished(LocationReportStage.NORMALIZATION)
        first.complete(LocationReportOutcome.SUCCESS)
        first.complete(LocationReportOutcome.FAILED)
        clock.advance(7)
        second.complete(LocationReportOutcome.CANCELLED)

        val snapshot = metrics.snapshot()
        assertEquals(2, snapshot.started)
        assertEquals(2, snapshot.peakInFlight)
        assertEquals(0, snapshot.currentlyInFlight)
        assertEquals(1, snapshot.outcomes.getValue(LocationReportOutcome.SUCCESS))
        assertEquals(1, snapshot.outcomes.getValue(LocationReportOutcome.CANCELLED))
        assertEquals(0, snapshot.outcomes.getValue(LocationReportOutcome.FAILED))
        assertEquals(1, snapshot.stages.getValue(LocationReportStage.CALLBACK_QUEUE).count)
        assertEquals(3, snapshot.stages.getValue(LocationReportStage.CALLBACK_QUEUE).totalNanos)
        assertEquals(5, snapshot.stages.getValue(LocationReportStage.NORMALIZATION).totalNanos)
        assertEquals(2, snapshot.total.count)
        assertEquals(22, snapshot.total.totalNanos)
        assertEquals(10, snapshot.total.minimumNanos)
        assertEquals(12, snapshot.total.maximumNanos)
    }

    @Test
    fun `unfinished and zero-duration stages close at terminal for every outcome`() {
        val clock = FakeClock()
        val metrics = AggregatingLocationReportMetrics(clock)
        LocationReportOutcome.entries.forEach { outcome ->
            val report = metrics.newReport()
            report.stageStarted(LocationReportStage.PUBLISH_SEND)
            report.stageFinished(LocationReportStage.PUBLISH_SEND)
            report.stageStarted(LocationReportStage.PUBACK_WAIT)
            clock.advance(4)
            report.complete(outcome)
        }
        val snapshot = metrics.snapshot()
        LocationReportOutcome.entries.forEach { assertEquals(1, snapshot.outcomes.getValue(it)) }
        assertEquals(4, snapshot.stages.getValue(LocationReportStage.PUBLISH_SEND).count)
        assertEquals(0, snapshot.stages.getValue(LocationReportStage.PUBLISH_SEND).totalNanos)
        assertEquals(0, snapshot.stages.getValue(LocationReportStage.PUBLISH_SEND).minimumNanos)
        assertEquals(0, snapshot.stages.getValue(LocationReportStage.PUBLISH_SEND).maximumNanos)
        assertEquals(4, snapshot.stages.getValue(LocationReportStage.PUBACK_WAIT).count)
        assertEquals(16, snapshot.stages.getValue(LocationReportStage.PUBACK_WAIT).totalNanos)
    }

    @Test
    fun `snapshot schema cannot retain private report material`() {
        val text = AggregatingLocationReportMetrics(FakeClock()).snapshot().toString()
        listOf(
            "41.3874",
            "activity-secret-123",
            "participant-secret-456",
            "opaque-topic-token",
            "session-key-bytes",
            "encrypted-payload-bytes",
            "broker-password",
            "Alice Secret Name",
        ).forEach { assertFalse(text.contains(it, ignoreCase = true), text) }
    }

    private class FakeClock : MonotonicClock {
        private var nanos = 0L

        override fun nowNanos(): Long = nanos

        fun advance(delta: Long) {
            nanos += delta
        }
    }
}
