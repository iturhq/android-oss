/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.core.model

import cat.itur.app.core.domain.id.IturActivityId
import cat.itur.app.core.domain.id.UserId
import java.util.Date

/**
 * The location of an activity's participant.
 */
data class ParticipantLocation(
    val activityId: IturActivityId,
    val userId: UserId,
    val userName: String,
    val location: Location,
    val recordedAt: Date? = null,
)
