/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map.ui.components.map

import cat.itur.app.core.domain.id.IturActivityId
import cat.itur.app.core.domain.id.UserId
import cat.itur.app.core.model.Location
import cat.itur.app.core.model.ParticipantLocation
import cat.itur.app.core.model.ParticipantSignal
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RemotePointerStoreTest {
    private val activity = IturActivityId("IncrementalMapOne001")
    private val organizer = UserId("organizer")
    private val self = UserId("self")
    private val first = UserId("first")
    private val second = UserId("second")
    private val base = listOf(
        location(organizer, 41.0, "Organizer"),
        location(self, 41.1, "Me"),
        location(first, 41.2, "First"),
        location(second, 41.3, "Second"),
    )

    @Test
    fun `moving one participant reuses organizer and unchanged participant features`() {
        val store = RemotePointerStore()
        val initial = update(store)
        val organizerFeature = store.featureFor(organizer)
        val secondFeature = store.featureFor(second)
        val moved = base.map {
            if (it.userId == first) it.copy(location = it.location.copy(latitude = 42.0)) else it
        }

        val changed = update(store, locations = moved)

        assertNull(changed.organizerFeatures)
        assertNotNull(changed.participantFeatures)
        assertSame(organizerFeature, store.featureFor(organizer))
        assertSame(secondFeature, store.featureFor(second))
        assertEquals(initial.statistics.featureBuilds + 1, changed.statistics.featureBuilds)
        assertEquals(initial.statistics.organizerSourceUpdates, changed.statistics.organizerSourceUpdates)
        assertEquals(initial.statistics.participantSourceUpdates + 1, changed.statistics.participantSourceUpdates)
    }

    @Test
    fun `recency tick within the same state performs no feature build or source reset`() {
        val store = RemotePointerStore()
        val initial = update(store)
        val organizerFeature = store.featureFor(organizer)
        val participantFeature = store.featureFor(first)

        val tick = update(store, nowMillis = NOW + 1_000L)

        assertNull(tick.organizerFeatures)
        assertNull(tick.participantFeatures)
        assertSame(organizerFeature, store.featureFor(organizer))
        assertSame(participantFeature, store.featureFor(first))
        assertEquals(initial.statistics, tick.statistics)
    }

    @Test
    fun `one participant crossing a recency threshold only resets participant source`() {
        val store = RemotePointerStore()
        val nearThreshold = base.map {
            if (it.userId == first) it.copy(recordedAt = Date(NOW - 14_999L)) else it
        }
        update(store, locations = nearThreshold)
        val organizerFeature = store.featureFor(organizer)
        val secondFeature = store.featureFor(second)

        val aged = update(store, locations = nearThreshold, nowMillis = NOW + 1L)

        assertNull(aged.organizerFeatures)
        assertNotNull(aged.participantFeatures)
        assertSame(organizerFeature, store.featureFor(organizer))
        assertSame(secondFeature, store.featureFor(second))
        assertEquals(0.6f, store.featureFor(first)?.getNumberProperty("opacity")?.toFloat())
    }

    @Test
    fun `organizer movement only updates organizer source`() {
        val store = RemotePointerStore()
        update(store)
        val participantFeature = store.featureFor(first)
        val moved = base.map {
            if (it.userId == organizer) it.copy(location = it.location.copy(longitude = 3.0)) else it
        }

        val changed = update(store, locations = moved)

        assertNotNull(changed.organizerFeatures)
        assertNull(changed.participantFeatures)
        assertSame(participantFeature, store.featureFor(first))
    }

    @Test
    fun `add remove name signal and bearing changes touch only required presentations`() {
        val store = RemotePointerStore()
        var locations = base
        val initial = update(store)
        val organizerFeature = store.featureFor(organizer)
        val secondFeature = store.featureFor(second)

        locations = locations + location(UserId("added"), 41.4, "Added")
        val added = update(store, locations = locations)
        assertNull(added.organizerFeatures)
        assertEquals(initial.statistics.featureBuilds + 1, added.statistics.featureBuilds)

        locations = locations.filterNot { it.userId == first }
        val removed = update(store, locations = locations)
        assertNull(removed.organizerFeatures)
        assertTrue(removed.participantFeatures.orEmpty().none { it.getStringProperty("id") == first.value })
        assertSame(secondFeature, store.featureFor(second))

        locations = locations.map { if (it.userId == second) it.copy(userName = "Renamed") else it }
        val renamed = update(store, locations = locations)
        assertNull(renamed.organizerFeatures)
        assertEquals("Renamed", store.featureFor(second)?.getStringProperty("label"))

        val signalled = update(
            store,
            locations = locations,
            signals = mapOf(second to ParticipantSignal.NEEDS_HELP),
        )
        assertNull(signalled.organizerFeatures)
        assertEquals(
            "marker-other-needs-help-directional",
            store.featureFor(second)?.getStringProperty("marker"),
        )

        locations = locations.map {
            if (it.userId == second) {
                it.copy(location = it.location.copy(bearingDegrees = 90f, bearingAccuracyDegrees = 2f))
            } else {
                it
            }
        }
        val bearing = update(
            store,
            locations = locations,
            signals = mapOf(second to ParticipantSignal.NEEDS_HELP),
        )
        assertNull(bearing.organizerFeatures)
        assertEquals(90f, store.featureFor(second)?.getNumberProperty("bearing")?.toFloat())
        assertSame(organizerFeature, store.featureFor(organizer))
    }

    @Test
    fun `organizer replacement updates both role sources and clears a missing organizer`() {
        val store = RemotePointerStore()
        update(store)

        val replaced = update(store, organizerId = first)

        assertNotNull(replaced.organizerFeatures)
        assertNotNull(replaced.participantFeatures)
        assertEquals("marker-organizer-okay-directional", store.featureFor(first)?.getStringProperty("marker"))
        assertEquals("marker-other-okay-directional", store.featureFor(organizer)?.getStringProperty("marker"))

        val withoutOrganizer = update(
            store,
            organizerId = first,
            locations = base.filterNot { it.userId == first },
        )
        assertEquals(emptyList(), withoutOrganizer.organizerFeatures)
    }

    @Test
    fun `style recreation replays both sources without rebuilding features`() {
        val store = RemotePointerStore()
        val initial = update(store, styleGeneration = 1)
        val organizerFeature = store.featureFor(organizer)
        val participantFeature = store.featureFor(first)

        val recreated = update(store, styleGeneration = 2)

        assertNotNull(recreated.organizerFeatures)
        assertNotNull(recreated.participantFeatures)
        assertSame(organizerFeature, store.featureFor(organizer))
        assertSame(participantFeature, store.featureFor(first))
        assertEquals(initial.statistics.featureBuilds, recreated.statistics.featureBuilds)
    }

    @Test
    fun `activity switch rebuilds presentations even when participant values match`() {
        val store = RemotePointerStore()
        val initial = update(store)
        val oldOrganizer = store.featureFor(organizer)
        val oldParticipant = store.featureFor(first)

        val switched = update(store, activityId = IturActivityId("IncrementalMapTwo002"))

        assertNotNull(switched.organizerFeatures)
        assertNotNull(switched.participantFeatures)
        assertTrue(oldOrganizer !== store.featureFor(organizer))
        assertTrue(oldParticipant !== store.featureFor(first))
        assertEquals(initial.statistics.featureBuilds + 3, switched.statistics.featureBuilds)
    }

    @Test
    fun `current user remains excluded from remote features`() {
        val store = RemotePointerStore()

        val changed = update(store, signals = mapOf(self to ParticipantSignal.DELAYED))

        assertNull(store.featureFor(self))
        assertTrue(changed.participantFeatures.orEmpty().none { it.getStringProperty("id") == self.value })
    }

    @Test
    fun `bearing expiry rebuilds only remote presentations`() {
        val store = RemotePointerStore()
        val initial = update(store, nowMillis = NOW)
        val selfBuilds = initial.statistics.featureBuilds

        val expired = update(store, nowMillis = NOW + MAX_POINTER_BEARING_AGE_MILLIS + 1)

        assertNotNull(expired.organizerFeatures)
        assertNotNull(expired.participantFeatures)
        assertEquals(selfBuilds + 3, expired.statistics.featureBuilds)
        assertEquals(
            "marker-organizer-okay-neutral",
            store.featureFor(organizer)?.getStringProperty("marker"),
        )
    }

    @Suppress("LongParameterList")
    private fun update(
        store: RemotePointerStore,
        activityId: IturActivityId = activity,
        organizerId: UserId = organizer,
        locations: List<ParticipantLocation> = base,
        signals: Map<UserId, ParticipantSignal> = emptyMap(),
        styleGeneration: Long = 1,
        nowMillis: Long = NOW,
    ) = store.update(
        RemotePointerInput(
            activityId = activityId,
            currentUserId = self,
            organizerId = organizerId,
            locations = locations,
            signals = signals,
            styleGeneration = styleGeneration,
            nowMillis = nowMillis,
            recencyThresholds = LocationRecencyThresholds(),
        ),
    )

    private fun location(userId: UserId, latitude: Double, name: String) = ParticipantLocation(
        activityId = activity,
        userId = userId,
        userName = name,
        location = Location(
            latitude = latitude,
            longitude = 2.0,
            providerTimestampMillis = NOW,
            bearingDegrees = 45f,
            bearingAccuracyDegrees = 5f,
        ),
        recordedAt = Date(NOW),
    )

    private companion object {
        const val NOW = 1_000_000L
    }
}
