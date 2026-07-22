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
    // The time the activity was created.
    val createdOn: Date = Calendar.getInstance().time,
    // Scheduled or actual start time. Defaults to createdOn for immediate Android-created activities.
    val startTime: Date = createdOn,
    // Set once the activity reaches a terminal status.
    val finishedOn: Date? = null,
    // Whether the activity is exposed to public surfaces. Android-created activities default to false.
    val listed: Boolean = false,
    // User IDs that have requested organiser attention (see UC-ACTIVITY-005).
    val attentionRequests: List<UserId> = emptyList(),
)

enum class IturActivityStatus {
    // The activity is not yet ready to go live.
    DRAFT,

    // Scheduled/prepared activity that is not yet ongoing.
    READY,

    // The activity has started.
    ONGOING,

    // The activity has finished successfully.
    FINISHED,

    // The activity was cancelled before or during operation.
    CANCELLED,
}
