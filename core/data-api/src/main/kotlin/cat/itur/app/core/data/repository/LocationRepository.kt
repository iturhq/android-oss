/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.core.data.repository

import cat.itur.app.core.domain.id.IturActivityId
import cat.itur.app.core.domain.id.UserId
import cat.itur.app.core.model.Location
import cat.itur.app.core.model.ParticipantLocation

/**
 * A repository to handle the locations of an ongoing activity's participants.
 */
interface LocationRepository {
    /**
     * The locations of all participants in an activity.
     */
    suspend fun getForActivity(activityId: IturActivityId): List<ParticipantLocation>

    /**
     * Updates the location of a participant in an activity.
     */
    suspend fun updateForParticipant(
        userId: UserId,
        activityId: IturActivityId,
        location: Location,
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
