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
import kotlinx.coroutines.flow.emptyFlow

/**
 * The minimum authoritative activity state needed by a location transport to observe members.
 * Keeping this separate from [cat.itur.app.core.model.IturActivity] prevents location adapters
 * from fetching or retaining unrelated activity state such as status and participant signals.
 */
data class LocationObservationScope(
    val activityId: IturActivityId,
    val organizerId: UserId,
    val participantIds: List<UserId>,
) {
    val memberIds: List<UserId> = (participantIds + organizerId).distinct()
}

/**
 * A repository to handle the locations of an ongoing activity's participants.
 */
interface LocationRepository {
    /** Starts an identifier-free report measurement; non-instrumented backends remain no-op. */
    fun newReportMeasurement(): LocationReportMeasurement = LocationReportMeasurement.None

    /**
     * The current locations of all participants in an activity. A caller entering an activity
     * must first pass its authoritative membership to [reconcileObservation]; subsequent reads
     * of an already-observed snapshot do not perform membership or transport reconciliation.
     */
    suspend fun getForActivity(activityId: IturActivityId): List<ParticipantLocation>

    /**
     * Observes the process-local location snapshot for [activityId]. Implementations backed by
     * push transports update this flow directly from their callbacks. The default keeps existing
     * repositories source-compatible while they adopt a native listener.
     */
    fun observeForActivity(activityId: IturActivityId): Flow<List<ParticipantLocation>> = emptyFlow()

    /**
     * Reconciles any transport subscriptions needed to keep [observeForActivity] current using
     * the caller's authoritative [scope]. This does not read either activity membership or the
     * location snapshot itself. Pull-only repositories need no extra work.
     */
    suspend fun reconcileObservation(scope: LocationObservationScope) = Unit

    /**
     * Updates the location of a participant in an activity.
     */
    suspend fun updateForParticipant(
        userId: UserId,
        activityId: IturActivityId,
        location: Location,
        measurement: LocationReportMeasurement = LocationReportMeasurement.None,
    )

    /**
     * Removes all location records for the given activity.
     */
    suspend fun removeForActivity(activityId: IturActivityId)

    /**
     * Removes the location record of a single participant in an activity, leaving other
     * participants' location records untouched.
     */
    suspend fun removeForParticipant(userId: UserId, activityId: IturActivityId)
}
