/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.model

import com.nohex.itur.core.domain.id.IturActivityId
import com.nohex.itur.core.domain.id.UserId

/**
 * The location of an activity's participant.
 */
data class ParticipantLocation(
    val activityId: IturActivityId,
    val userId: UserId,
    val userName: String,
    val location: Location,
)
