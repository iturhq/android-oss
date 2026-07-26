/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.data.repository

import com.nohex.itur.core.domain.id.IturActivityId
import com.nohex.itur.core.domain.id.UserId
import com.nohex.itur.core.model.Location
import com.nohex.itur.core.model.ParticipantLocation

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
}
