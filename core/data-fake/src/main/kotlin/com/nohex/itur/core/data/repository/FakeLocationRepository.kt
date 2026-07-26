/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.data.repository

import com.nohex.itur.core.domain.id.IturActivityId
import com.nohex.itur.core.domain.id.UserId
import com.nohex.itur.core.model.Location
import com.nohex.itur.core.model.ParticipantLocation
import kotlin.random.Random

// Greenwich Park
private val defaultLocation = Location(latitude = 51.4792842, longitude = 0.0)

// Approximation of meters in a degree of latitude/longitude
internal const val METERS_PER_DEGREE = 111_000.0

class FakeLocationRepository constructor(
    private val activityRepository: ActivityRepository,
    initialLocations: Map<IturActivityId, Map<UserId, Location>> = emptyMap(),
) : LocationRepository {

    private val locationsByActivity =
        mutableMapOf<IturActivityId, MutableMap<UserId, Location>>().apply {
            initialLocations.forEach { (activityId, userLocations) ->
                put(activityId, userLocations.toMutableMap())
            }
        }

    override suspend fun getForActivity(activityId: IturActivityId): List<ParticipantLocation> {
        val result = activityRepository.getActivity(activityId)
        return when (result) {
            is DataResult.NotFound -> emptyList()
            is DataResult.Error -> emptyList()
            is DataResult.Success -> {
                val activity = result.data
                val activityLocations = locationsByActivity.getOrPut(activityId) { mutableMapOf() }

                activity.participantIds.map { participantId ->
                    val currentLocation = activityLocations.getOrPut(participantId) {
                        // First-time location: start with a location up to 50m from the organiser's
                        // location, or the default location if the organiser is not found.
                        val initialLocation = activityLocations[activity.organizerId] ?: defaultLocation
                        jitterLocation(initialLocation, 50.0)
                    }

                    // Jitter the location slightly (up to 5m).
                    val newLocation = jitterLocation(currentLocation, maxOffset = 5.0)
                    activityLocations[participantId] = newLocation

                    ParticipantLocation(
                        activityId = activityId,
                        userId = participantId,
                        location = newLocation,
                        userName = "User ".plus(participantId.value),
                    )
                }
            }
        }
    }

    override suspend fun removeForActivity(activityId: IturActivityId) {
        locationsByActivity.remove(activityId)
    }

    override suspend fun updateForParticipant(
        userId: UserId,
        activityId: IturActivityId,
        location: Location,
    ) {
        locationsByActivity.getOrPut(activityId) { mutableMapOf() }
            .apply {
                this[userId] = location
            }
    }

    /**
     * Jitters a location by a very small offset, simulating GPS noise.
     */
    private fun jitterLocation(origin: Location, maxOffset: Double): Location {
        val latOffset = Random.nextDouble(-maxOffset, maxOffset) / METERS_PER_DEGREE
        val lonOffset = Random.nextDouble(-maxOffset, maxOffset) / METERS_PER_DEGREE
        return Location(
            latitude = origin.latitude + latOffset,
            longitude = origin.longitude + lonOffset,
        )
    }
}
