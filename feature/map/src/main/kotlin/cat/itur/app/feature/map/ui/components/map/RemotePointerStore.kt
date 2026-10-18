/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map.ui.components.map

import cat.itur.app.core.domain.id.IturActivityId
import cat.itur.app.core.domain.id.UserId
import cat.itur.app.core.model.ParticipantLocation
import cat.itur.app.core.model.ParticipantSignal
import org.maplibre.geojson.Feature

/**
 * Preserves remote marker features while repository snapshots remain whole lists.
 *
 * MapLibre replaces an entire GeoJSON source at once. This store first diffs the snapshot by
 * participant so unchanged features are reused and only an affected role source is reset.
 */
internal class RemotePointerStore {
    private var activityId: IturActivityId? = null
    private var styleGeneration = Long.MIN_VALUE
    private var presentations = linkedMapOf<UserId, CachedPointer>()
    private var organizerFeatures: List<Feature> = emptyList()
    private var participantFeatures: List<Feature> = emptyList()
    private var statistics = RemotePointerStoreStatistics()

    internal fun featureFor(userId: UserId): Feature? = presentations[userId]?.feature

    fun update(input: RemotePointerInput): RemotePointerUpdate {
        val activityChanged = activityId != input.activityId
        val styleChanged = styleGeneration != input.styleGeneration
        val previous = if (activityChanged) emptyMap() else presentations
        val next = LinkedHashMap<UserId, CachedPointer>(input.locations.size)

        input.locations.forEach { location ->
            if (location.userId in next) return@forEach
            next[location.userId] = pointerFor(location, input, previous[location.userId])
        }

        val nextOrganizerFeatures = input.organizerId
            ?.let(next::get)
            ?.feature
            ?.let(::listOf)
            .orEmpty()
        val nextParticipantFeatures = next.values.mapNotNull { cached ->
            cached.feature.takeIf { cached.key?.role == PointerRole.OTHER }
        }
        val organizerChanged = styleChanged || !organizerFeatures.sameInstancesAs(nextOrganizerFeatures)
        val participantsChanged = styleChanged || !participantFeatures.sameInstancesAs(nextParticipantFeatures)

        activityId = input.activityId
        styleGeneration = input.styleGeneration
        presentations = next
        organizerFeatures = nextOrganizerFeatures
        participantFeatures = nextParticipantFeatures
        statistics = statistics.copy(
            organizerSourceUpdates = statistics.organizerSourceUpdates + organizerChanged.asCount(),
            participantSourceUpdates = statistics.participantSourceUpdates + participantsChanged.asCount(),
        )
        return RemotePointerUpdate(
            organizerFeatures = nextOrganizerFeatures.takeIf { organizerChanged },
            participantFeatures = nextParticipantFeatures.takeIf { participantsChanged },
            statistics = statistics,
        )
    }

    private fun pointerFor(
        location: ParticipantLocation,
        input: RemotePointerInput,
        cached: CachedPointer?,
    ): CachedPointer {
        val role = when (location.userId) {
            input.organizerId -> PointerRole.ORGANIZER
            input.currentUserId -> null
            else -> PointerRole.OTHER
        }
        val signal = input.signals[location.userId]
        val opacity = location.markerOpacity(input.nowMillis, input.recencyThresholds)
        val key = role?.let {
            FeatureKey(
                activityId = input.activityId,
                role = it,
                latitude = location.location.latitude,
                longitude = location.location.longitude,
                label = location.userName,
                signal = signal,
                bearing = pointerBearingRotation(location.location, input.nowMillis),
                opacity = opacity,
            )
        }
        val feature = when {
            key == null -> null
            cached?.key == key -> cached.feature
            else -> remotePointerFeature(
                participantLocation = location,
                role = role,
                signal = signal,
                opacity = opacity,
                nowMillis = input.nowMillis,
            ).also {
                statistics = statistics.copy(featureBuilds = statistics.featureBuilds + 1)
            }
        }
        return CachedPointer(key, feature)
    }

    private fun List<Feature>.sameInstancesAs(other: List<Feature>): Boolean {
        if (size != other.size) return false
        return indices.all { this[it] === other[it] }
    }

    private fun Boolean.asCount(): Long = if (this) 1L else 0L

    private data class CachedPointer(
        val key: FeatureKey?,
        val feature: Feature?,
    )

    private data class FeatureKey(
        val activityId: IturActivityId?,
        val role: PointerRole,
        val latitude: Double,
        val longitude: Double,
        val label: String,
        val signal: ParticipantSignal?,
        val bearing: Float?,
        val opacity: Float,
    )
}

internal data class RemotePointerInput(
    val activityId: IturActivityId?,
    val currentUserId: UserId?,
    val organizerId: UserId?,
    val locations: List<ParticipantLocation>,
    val signals: Map<UserId, ParticipantSignal>,
    val styleGeneration: Long,
    val nowMillis: Long,
    val recencyThresholds: LocationRecencyThresholds,
)

internal data class RemotePointerUpdate(
    /** Null keeps the current organiser source; an empty list clears it. */
    val organizerFeatures: List<Feature>?,
    /** Null keeps the current participant source; an empty list clears it. */
    val participantFeatures: List<Feature>?,
    val statistics: RemotePointerStoreStatistics,
)

internal data class RemotePointerStoreStatistics(
    val featureBuilds: Long = 0,
    val organizerSourceUpdates: Long = 0,
    val participantSourceUpdates: Long = 0,
)
