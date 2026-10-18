/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map

import android.os.Debug
import android.view.Choreographer
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cat.itur.app.core.domain.id.IturActivityId
import cat.itur.app.core.domain.id.UserId
import cat.itur.app.core.model.Location
import cat.itur.app.core.model.ParticipantLocation
import cat.itur.app.core.model.ParticipantSignal
import cat.itur.app.feature.map.ui.components.map.LocationRecencyThresholds
import cat.itur.app.feature.map.ui.components.map.MapLibreViewInput
import cat.itur.app.feature.map.ui.components.map.PointerRole
import cat.itur.app.feature.map.ui.components.map.RemotePointerInput
import cat.itur.app.feature.map.ui.components.map.RemotePointerStore
import cat.itur.app.feature.map.ui.components.map.accessibilityDescription
import cat.itur.app.feature.map.ui.components.map.remotePointerFeature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.geojson.FeatureCollection
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Device-side comparison of whole-list rebuilding and the keyed renderer. */
@RunWith(AndroidJUnit4::class)
class RemotePointerPerformanceTest {
    @Volatile
    private var blackHole: Any? = null

    @Test
    fun smallAndUpperCapacityMovementProfilesRecordFrameWorkAllocationsAndDrops() {
        listOf(3, 100).forEach { groupSize ->
            val snapshots = movementSnapshots(groupSize, MEASURED_FRAMES + WARMUP_FRAMES)
            val legacy = measureFrames(legacyOperation(snapshots), snapshots.size)
            val incremental = measureFrames(incrementalOperation(snapshots), snapshots.size)

            println(
                "GPSD57A_PROFILE participants=$groupSize " +
                    "legacy_cpu_nanos=${legacy.mainThreadCpuNanos} " +
                    "legacy_allocations=${legacy.allocations} " +
                    "legacy_avg_frame_nanos=${legacy.averageFrameNanos} " +
                    "legacy_max_frame_nanos=${legacy.maxFrameNanos} " +
                    "legacy_dropped_frames=${legacy.droppedFrames} " +
                    "incremental_cpu_nanos=${incremental.mainThreadCpuNanos} " +
                    "incremental_allocations=${incremental.allocations} " +
                    "incremental_avg_frame_nanos=${incremental.averageFrameNanos} " +
                    "incremental_max_frame_nanos=${incremental.maxFrameNanos} " +
                    "incremental_dropped_frames=${incremental.droppedFrames}",
            )
            assertTrue(
                "incremental allocation count regressed at size $groupSize",
                legacy.allocations == 0 || incremental.allocations < legacy.allocations,
            )
        }
    }

    @Test
    fun markerAndAccessibilityOutputsRemainCorrectOnDevice() {
        val store = RemotePointerStore()
        val locations = movementSnapshots(groupSize = 3, count = 1).single()

        store.update(input(locations, mapOf(UserId("participant-2") to ParticipantSignal.NEEDS_HELP)))

        assertEquals(
            "marker-organizer-okay-directional",
            store.featureFor(ORGANIZER_ID)?.getStringProperty("marker"),
        )
        assertNull(store.featureFor(SELF_ID))
        assertEquals(
            "marker-other-needs-help-directional",
            store.featureFor(UserId("participant-2"))?.getStringProperty("marker"),
        )
        assertEquals(
            "Participant locations. Participant 0, organiser, okay, Participant 0 location is current; " +
                "Participant 1, self, okay, Participant 1 location is current; " +
                "Participant 2, participant, needs-help, Participant 2 location is current",
            accessibility(locations, mapOf(UserId("participant-2") to ParticipantSignal.NEEDS_HELP)),
        )

        val removed = store.update(input(locations.dropLast(1)))
        assertTrue(removed.participantFeatures.orEmpty().isEmpty())
        assertNull(store.featureFor(UserId("participant-2")))
    }

    private fun legacyOperation(snapshots: List<List<ParticipantLocation>>): (Int) -> Unit = { index ->
        val locations = snapshots[index % snapshots.size]
        val organizerFeatures = listOf(
            remotePointerFeature(
                participantLocation = locations.first(),
                role = PointerRole.ORGANIZER,
                signal = null,
                opacity = 1f,
                nowMillis = NOW,
            ),
        )
        val participantFeatures = locations
            .asSequence()
            .filter { it.userId != ORGANIZER_ID && it.userId != SELF_ID }
            .map {
                remotePointerFeature(
                    participantLocation = it,
                    role = PointerRole.OTHER,
                    signal = null,
                    opacity = 1f,
                    nowMillis = NOW,
                )
            }
            .toList()
        blackHole = Triple(
            FeatureCollection.fromFeatures(organizerFeatures),
            FeatureCollection.fromFeatures(participantFeatures),
            accessibility(locations),
        )
    }

    private fun incrementalOperation(snapshots: List<List<ParticipantLocation>>): (Int) -> Unit {
        val store = RemotePointerStore()
        return { index ->
            val locations = snapshots[index % snapshots.size]
            val update = store.update(input(locations))
            blackHole = Triple(
                update.organizerFeatures?.let(FeatureCollection::fromFeatures),
                update.participantFeatures?.let(FeatureCollection::fromFeatures),
                accessibility(locations),
            )
        }
    }

    private fun input(
        locations: List<ParticipantLocation>,
        signals: Map<UserId, ParticipantSignal> = emptyMap(),
    ) = RemotePointerInput(
        activityId = ACTIVITY_ID,
        currentUserId = SELF_ID,
        organizerId = ORGANIZER_ID,
        locations = locations,
        signals = signals,
        styleGeneration = 1,
        nowMillis = NOW,
        recencyThresholds = LocationRecencyThresholds(),
    )

    private fun accessibility(
        locations: List<ParticipantLocation>,
        signals: Map<UserId, ParticipantSignal> = emptyMap(),
    ) = MapLibreViewInput(
        styleUrl = "benchmark://style",
        isActivityOngoing = true,
        activityId = ACTIVITY_ID,
        locationPermissionGranted = true,
        currentUserId = SELF_ID,
        organizerId = ORGANIZER_ID,
        participantLocations = locations,
        participantSignals = signals,
    ).accessibilityDescription(NOW)

    private fun measureFrames(operation: (Int) -> Unit, snapshotCount: Int): FrameProfile {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            repeat(WARMUP_FRAMES) { operation(it % snapshotCount) }
        }
        val latch = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        val profile = MutableFrameProfile()

        @Suppress("DEPRECATION")
        val refreshRate = instrumentation.targetContext
            .getSystemService(WindowManager::class.java)
            .defaultDisplay
            .refreshRate
        val frameBudgetNanos = (1_000_000_000.0 / refreshRate).toLong()

        Debug.startAllocCounting()
        instrumentation.runOnMainSync {
            Choreographer.getInstance().postFrameCallback(
                object : Choreographer.FrameCallback {
                    private var frame = 0
                    private var previousFrameNanos = 0L

                    override fun doFrame(frameTimeNanos: Long) {
                        try {
                            if (previousFrameNanos != 0L) {
                                val interval = frameTimeNanos - previousFrameNanos
                                profile.frameNanos += interval
                                profile.maxFrameNanos = maxOf(profile.maxFrameNanos, interval)
                                profile.droppedFrames += maxOf(
                                    0,
                                    ((interval + frameBudgetNanos / 2) / frameBudgetNanos).toInt() - 1,
                                )
                            }
                            previousFrameNanos = frameTimeNanos
                            val allocationsBefore = Debug.getThreadAllocCount()
                            val cpuBefore = Debug.threadCpuTimeNanos()
                            operation((WARMUP_FRAMES + frame) % snapshotCount)
                            profile.mainThreadCpuNanos += Debug.threadCpuTimeNanos() - cpuBefore
                            profile.allocations += Debug.getThreadAllocCount() - allocationsBefore
                            frame++
                            if (frame < MEASURED_FRAMES) {
                                Choreographer.getInstance().postFrameCallback(this)
                            } else {
                                latch.countDown()
                            }
                        } catch (cause: Throwable) {
                            failure.set(cause)
                            latch.countDown()
                        }
                    }
                },
            )
        }
        assertTrue("frame profile timed out", latch.await(20, TimeUnit.SECONDS))
        Debug.stopAllocCounting()
        failure.get()?.let { throw it }
        assertEquals(MEASURED_FRAMES - 1, profile.frameNanos.size)
        return FrameProfile(
            mainThreadCpuNanos = profile.mainThreadCpuNanos,
            allocations = profile.allocations,
            averageFrameNanos = profile.frameNanos.average().toLong(),
            maxFrameNanos = profile.maxFrameNanos,
            droppedFrames = profile.droppedFrames,
        )
    }

    private fun movementSnapshots(groupSize: Int, count: Int): List<List<ParticipantLocation>> {
        val base = (0 until groupSize).map { index ->
            val userId = when (index) {
                0 -> ORGANIZER_ID
                1 -> SELF_ID
                else -> UserId("participant-$index")
            }
            ParticipantLocation(
                activityId = ACTIVITY_ID,
                userId = userId,
                userName = "Participant $index",
                location = Location(
                    latitude = 41.0 + index / 10_000.0,
                    longitude = 2.0,
                    providerTimestampMillis = NOW,
                    bearingDegrees = 45f,
                    bearingAccuracyDegrees = 5f,
                ),
                recordedAt = Date(NOW),
            )
        }
        return (0 until count).map { frame ->
            base.mapIndexed { index, location ->
                if (index == MOVING_PARTICIPANT_INDEX) {
                    location.copy(
                        location = location.location.copy(
                            latitude = location.location.latitude + frame / 1_000_000.0,
                        ),
                    )
                } else {
                    location
                }
            }
        }
    }

    private data class FrameProfile(
        val mainThreadCpuNanos: Long,
        val allocations: Int,
        val averageFrameNanos: Long,
        val maxFrameNanos: Long,
        val droppedFrames: Int,
    )

    private class MutableFrameProfile {
        var mainThreadCpuNanos = 0L
        var allocations = 0
        val frameNanos = mutableListOf<Long>()
        var maxFrameNanos = 0L
        var droppedFrames = 0
    }

    private companion object {
        const val NOW = 1_000_000L
        const val WARMUP_FRAMES = 10
        const val MEASURED_FRAMES = 30
        const val MOVING_PARTICIPANT_INDEX = 2
        val ACTIVITY_ID = IturActivityId("IncrementalMapBench1")
        val ORGANIZER_ID = UserId("organizer")
        val SELF_ID = UserId("self")
    }
}
