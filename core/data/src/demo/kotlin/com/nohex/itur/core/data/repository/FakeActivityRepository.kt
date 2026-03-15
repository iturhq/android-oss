/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.data.repository

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import com.nohex.itur.core.domain.id.IturActivityId
import com.nohex.itur.core.domain.id.UserId
import com.nohex.itur.core.model.IturActivity
import com.nohex.itur.core.model.IturActivityStatus

class FakeActivityRepository(
    initialActivities: List<IturActivity> = emptyList(),
) : ActivityRepository {
    private val activities = mutableStateListOf<IturActivity>().apply { addAll(initialActivities) }

    override suspend fun getActivity(activityId: IturActivityId): DataResult<IturActivity> = activities
        .firstOrNull { it.id == activityId }
        ?.let { activity -> DataResult.Success(activity) }
        ?: DataResult.NotFound(activityId.value)

    override suspend fun getActivities(filter: ActivityFilter): DataResult<List<IturActivity>> = when (filter) {
        is ActivityFilter.ByOrganizer ->
            DataResult.Success(activities.filter { it.organizerId == filter.organizerId })

        is ActivityFilter.OngoingByOrganizer ->
            DataResult.Success(activities.filter { it.organizerId == filter.organizerId && it.status == IturActivityStatus.ONGOING })
    }

    override suspend fun updateActivityStatus(
        id: IturActivityId,
        newStatus: IturActivityStatus,
    ): DataResult<IturActivity> {
        val index = activities.indexOfFirst { it.id == id }
        if (index == -1) {
            return DataResult.NotFound(id.value)
        }

        activities[index] = activities[index].copy(status = newStatus)

        return DataResult.Success(activities[index])
    }

    override suspend fun deleteActivity(activityId: IturActivityId): DataResult<IturActivity> {
        val index = activities.indexOfFirst { it.id == activityId }
        if (index == -1) {
            return DataResult.NotFound(activityId.value)
        }

        val deletedActivity = activities[index]
        activities.remove(activities[index])

        return DataResult.Success(deletedActivity)
    }

    override suspend fun createActivity(organizerId: UserId): DataResult<IturActivity> {
        // Random 20-letter ID, similar to a Firebase one.
        val newId = (1..20)
            .map { ('0'..'9') + ('A'..'Z') + ('a'..'z') }
            .flatten()
            .shuffled()
            .take(20)
            .joinToString("")

        val newActivity = IturActivity(
            organizerId = organizerId,
            id = IturActivityId(newId),
            participantIds = emptyList(),
        )

        activities.add(newActivity)

        return DataResult.Success(newActivity)
    }

    override suspend fun addParticipant(
        activityId: IturActivityId,
        userId: UserId,
    ): DataResult<IturActivity> {
        val index = activities.indexOfFirst { it.id == activityId }
        if (index == -1) throw IllegalArgumentException("Activity not found")
        val activity = activities[index]
        activities[index] = activity.copy(
            participantIds = activity.participantIds + userId,
        )

        return DataResult.Success(activities[index])
    }

    override suspend fun requestAttention(activityId: IturActivityId, userId: UserId) {
        Log.d("FakeActivityRepo", "User ${userId.value} requested attention in activity ${activityId.value}")
    }

    override suspend fun removeParticipant(
        activityId: IturActivityId,
        userId: UserId,
    ): DataResult<IturActivity> {
        val index = activities.indexOfFirst { it.id == activityId }
        if (index == -1) return DataResult.NotFound(activityId.value)
        val activity = activities[index]
        activities[index] = activity.copy(
            participantIds = activity.participantIds - userId,
        )

        return DataResult.Success(activities[index])
    }
}
