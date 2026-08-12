/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.data.repository

import com.nohex.itur.core.domain.id.IturActivityId
import com.nohex.itur.core.domain.id.UserId
import com.nohex.itur.core.model.Broadcast
import com.nohex.itur.core.model.IturActivity
import com.nohex.itur.core.model.IturActivityStatus
import java.util.Date

interface ActivityRepository {
    /**
     * Retrieve the activities matching the filter.
     */
    suspend fun getActivities(filter: ActivityFilter): DataResult<List<IturActivity>>

    /**
     * Retrieve the activity with the given ID.
     */
    suspend fun getActivity(activityId: IturActivityId): DataResult<IturActivity>

    /**
     * The `ONGOING` activity (if any) [userId] is currently an active member of -- organiser or
     * participant. `Success(null)` means they are not active in any. Used to block starting or
     * joining a second one before attempting a write the backend would reject anyway.
     */
    suspend fun getActiveActivityId(userId: UserId): DataResult<IturActivityId?>

    /**
     * Creates a new activity for the given user.
     */
    suspend fun createActivity(organizerId: UserId): DataResult<IturActivity>

    /**
     * Update the status of the given activity.
     */
    suspend fun updateActivityStatus(
        id: IturActivityId,
        newStatus: IturActivityStatus,
    ): DataResult<IturActivity>

    /**
     * Creates a new activity for the given user.
     */
    suspend fun deleteActivity(activityId: IturActivityId): DataResult<IturActivity>

    /**
     * Adds a user to the activity's participants.
     *
     * Only participants in this list are considered for map updates.
     *
     * @returns The updated activity.
     */
    suspend fun addParticipant(activityId: IturActivityId, userId: UserId): DataResult<IturActivity>

    /**
     * Removes a user from the activity's participants.
     * @returns The updated activity.
     */
    suspend fun removeParticipant(activityId: IturActivityId, userId: UserId): DataResult<IturActivity>

    /** Compatibility entry point for the former binary attention request. */
    suspend fun requestAttention(activityId: IturActivityId, userId: UserId)

    /**
     * Operator broadcasts sent to the activity after [since] (or all of them, if `null`),
     * oldest first. Read-only from Android -- only `itur-admin` can send one (UC-ACTIVITY-007).
     */
    suspend fun getBroadcastsSince(activityId: IturActivityId, since: Date?): List<Broadcast>
}

sealed class ActivityFilter {
    // NOTE: There is no "All" filter on purpose,
    // no need to overwhelm the system by accident or otherwise.

    // Retrieve activities by its organizer.
    data class ByOrganizer(
        val organizerId: UserId,
    ) : ActivityFilter()

    // Retrieve ongoing activities by its organizer.
    data class OngoingByOrganizer(
        val organizerId: UserId,
    ) : ActivityFilter()

    // Retrieve ongoing activities containing the user as a participant.
    data class OngoingByParticipant(
        val participantId: UserId,
    ) : ActivityFilter()
}
