/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.core.data.repository

import cat.itur.app.core.domain.id.IturActivityId
import cat.itur.app.core.domain.id.UserId
import cat.itur.app.core.model.Location
import cat.itur.app.core.model.ParticipantLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val snapshotsByActivity = mutableMapOf<IturActivityId, MutableStateFlow<List<ParticipantLocation>>>()

    override suspend fun getForActivity(activityId: IturActivityId): List<ParticipantLocation> {
        val result = activityRepository.getActivity(activityId)
        return when (result) {
            is DataResult.NotFound -> emptyList()
            is DataResult.Error -> emptyList()
            is DataResult.Success -> {
                val activity = result.data
                val activityLocations = locationsByActivity.getOrPut(activityId) { mutableMapOf() }

                (activity.participantIds + activity.organizerId).distinct().map { participantId ->
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
                }.also { publishSnapshot(activityId, it) }
            }
        }
    }

    override fun observeForActivity(activityId: IturActivityId): Flow<List<ParticipantLocation>> =
        snapshotsByActivity.getOrPut(activityId) { MutableStateFlow(emptyList()) }.asStateFlow()

    override suspend fun removeForActivity(activityId: IturActivityId) {
        locationsByActivity.remove(activityId)
        publishSnapshot(activityId, emptyList())
    }

    override suspend fun removeForParticipant(userId: UserId, activityId: IturActivityId) {
        locationsByActivity[activityId]?.remove(userId)
        publishStoredSnapshot(activityId)
    }

    override suspend fun updateForParticipant(
        userId: UserId,
        activityId: IturActivityId,
        location: Location,
        measurement: LocationReportMeasurement,
    ) {
        locationsByActivity.getOrPut(activityId) { mutableMapOf() }
            .apply {
                this[userId] = location
            }
        publishStoredSnapshot(activityId)
    }

    private fun publishStoredSnapshot(activityId: IturActivityId) {
        val snapshot = locationsByActivity[activityId].orEmpty().map { (userId, location) ->
            ParticipantLocation(
                activityId = activityId,
                userId = userId,
                userName = "User ${userId.value}",
                location = location,
            )
        }
        publishSnapshot(activityId, snapshot)
    }

    private fun publishSnapshot(activityId: IturActivityId, snapshot: List<ParticipantLocation>) {
        snapshotsByActivity.getOrPut(activityId) { MutableStateFlow(emptyList()) }.value = snapshot
    }

    /**
     * Jitters a location by a very small offset, simulating GPS noise.
     */
    private fun jitterLocation(origin: Location, maxOffset: Double): Location {
        val latOffset = Random.nextDouble(-maxOffset, maxOffset) / METERS_PER_DEGREE
        val lonOffset = Random.nextDouble(-maxOffset, maxOffset) / METERS_PER_DEGREE
        return origin.copy(
            latitude = origin.latitude + latOffset,
            longitude = origin.longitude + lonOffset,
        )
    }
}
