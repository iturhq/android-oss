/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.model

import com.nohex.itur.core.domain.id.IturActivityId
import com.nohex.itur.core.domain.id.UserId
import java.util.Calendar
import java.util.Date

/**
 * Prefixed with Itur to avoid confusion with Android's own activities.
 */
data class IturActivity(
    val id: IturActivityId,
    val status: IturActivityStatus = IturActivityStatus.DRAFT,
    // The ID of the user that created the activity.
    val organizerId: UserId,
    // The user IDs of the participants.
    val participantIds: List<UserId>,
    // The time the activity started.
    val createdOn: Date = Calendar.getInstance().time,
)

enum class IturActivityStatus {
    // The activity is not yet ready to go live.
    DRAFT,

    // The activity has started.
    ONGOING,

    // The activity has finished successfully.
    FINISHED,
}
