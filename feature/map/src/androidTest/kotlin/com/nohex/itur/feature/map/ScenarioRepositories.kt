/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.feature.map

import android.content.Context
import com.nohex.itur.core.data.TestFixtures
import com.nohex.itur.core.data.repository.ActivityFilter
import com.nohex.itur.core.data.repository.ActivityRepository
import com.nohex.itur.core.data.repository.DataResult
import com.nohex.itur.core.data.repository.LocationRepository
import com.nohex.itur.core.data.repository.ParticipantSignalRepository
import com.nohex.itur.core.data.repository.SignInResult
import com.nohex.itur.core.data.repository.UserRepository
import com.nohex.itur.core.domain.id.IturActivityId
import com.nohex.itur.core.domain.id.UserId
import com.nohex.itur.core.domain.model.User
import com.nohex.itur.core.model.Broadcast
import com.nohex.itur.core.model.IturActivity
import com.nohex.itur.core.model.IturActivityStatus
import com.nohex.itur.core.model.Location
import com.nohex.itur.core.model.ParticipantLocation
import com.nohex.itur.core.model.ParticipantSignal
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger

class ScenarioUserRepository : UserRepository {
    val anonymous = User.AnonymousUser(TestFixtures.PARTICIPANT_1_ID)
    val registered = User.RegisteredUser(
        id = TestFixtures.ORGANIZER_ID,
        name = "William Henry Harrisson",
        email = "william.henry.harrison@example.com",
    )
    val participant = User.RegisteredUser(
        id = TestFixtures.PARTICIPANT_2_ID,
        name = "James Iredell",
        email = "james.iredell@example.com",
    )

    var current: User = anonymous
    var signInResult: SignInResult? = null

    override suspend fun getCurrentUser(): User = current

    override suspend fun getAll(ids: List<UserId>): List<User> = listOf(anonymous, registered, participant).filter { it.id in ids }

    override suspend fun signIn(context: Context): SignInResult {
        signInResult?.let { return it }
        current = registered
        return SignInResult.Success(current)
    }

    override suspend fun signOut() {
        current = anonymous
    }
}

class ScenarioActivityRepository :
    ActivityRepository,
    ParticipantSignalRepository {
    // The anonymous actor starts outside every activity. Join scenarios add them explicitly;
    // organizer and registered-participant cold-start scenarios retain their fixture membership.
    private val activities = TestFixtures.activities.map { activity ->
        activity.copy(
            participantIds = activity.participantIds - TestFixtures.PARTICIPANT_1_ID,
        )
    }.toMutableList()

    var createFailure: String? = null
    var addFailure: String? = null
    var removeFailure: Throwable? = null
    var updateFailure: Throwable? = null
    var getActivityFailuresRemaining = 0
    val addParticipantCount = AtomicInteger()
    val attentionRequestCount = AtomicInteger()

    fun replaceActivities(replacement: List<IturActivity>) {
        activities.clear()
        activities.addAll(replacement)
    }

    fun activity(id: IturActivityId): IturActivity? = activities.firstOrNull { it.id == id }

    override suspend fun getActivities(filter: ActivityFilter): DataResult<List<IturActivity>> = DataResult.Success(
        when (filter) {
            is ActivityFilter.ByOrganizer ->
                activities.filter { it.organizerId == filter.organizerId }

            is ActivityFilter.OngoingByOrganizer ->
                activities.filter {
                    it.organizerId == filter.organizerId &&
                        it.status == IturActivityStatus.ONGOING
                }

            is ActivityFilter.OngoingByParticipant ->
                activities.filter {
                    filter.participantId in it.participantIds &&
                        it.status == IturActivityStatus.ONGOING
                }
        },
    )

    override suspend fun getActivity(activityId: IturActivityId): DataResult<IturActivity> {
        if (getActivityFailuresRemaining > 0) {
            getActivityFailuresRemaining--
            return DataResult.Error("temporary resume failure")
        }
        return activity(activityId)?.let { DataResult.Success(it) }
            ?: DataResult.NotFound(activityId.value)
    }

    override suspend fun getActiveActivityId(userId: UserId): DataResult<IturActivityId?> = DataResult.Success(
        activities.firstOrNull { activity ->
            activity.status == IturActivityStatus.ONGOING &&
                (activity.organizerId == userId || userId in activity.participantIds)
        }?.id,
    )

    override suspend fun createActivity(organizerId: UserId): DataResult<IturActivity> {
        createFailure?.let { return DataResult.Error(it) }
        val activity = IturActivity(
            id = IturActivityId("createdActivity00001"),
            organizerId = organizerId,
            participantIds = emptyList(),
        )
        activities.add(activity)
        return DataResult.Success(activity)
    }

    override suspend fun updateActivityStatus(
        id: IturActivityId,
        newStatus: IturActivityStatus,
    ): DataResult<IturActivity> {
        updateFailure?.let { throw it }
        val index = activities.indexOfFirst { it.id == id }
        if (index < 0) return DataResult.NotFound(id.value)
        activities[index] = activities[index].copy(status = newStatus)
        return DataResult.Success(activities[index])
    }

    override suspend fun deleteActivity(
        activityId: IturActivityId,
    ): DataResult<IturActivity> {
        val activity = activity(activityId) ?: return DataResult.NotFound(activityId.value)
        activities.remove(activity)
        return DataResult.Success(activity)
    }

    override suspend fun addParticipant(
        activityId: IturActivityId,
        userId: UserId,
    ): DataResult<IturActivity> {
        addParticipantCount.incrementAndGet()
        addFailure?.let { return DataResult.Error(it) }
        val index = activities.indexOfFirst { it.id == activityId }
        if (index < 0) return DataResult.NotFound(activityId.value)
        activities[index] = activities[index].copy(
            participantIds = (activities[index].participantIds + userId).distinct(),
        )
        return DataResult.Success(activities[index])
    }

    override suspend fun removeParticipant(
        activityId: IturActivityId,
        userId: UserId,
    ): DataResult<IturActivity> {
        removeFailure?.let { throw it }
        val index = activities.indexOfFirst { it.id == activityId }
        if (index < 0) return DataResult.NotFound(activityId.value)
        activities[index] = activities[index].copy(
            participantIds = activities[index].participantIds - userId,
            participantSignals = activities[index].participantSignals - userId,
        )
        return DataResult.Success(activities[index])
    }

    override suspend fun setParticipantSignal(
        activityId: IturActivityId,
        userId: UserId,
        signal: ParticipantSignal,
    ): DataResult<IturActivity> {
        val index = activities.indexOfFirst { it.id == activityId }
        if (index < 0) return DataResult.NotFound(activityId.value)
        activities[index] = activities[index].copy(
            participantSignals = activities[index].participantSignals + (userId to signal),
        )
        return DataResult.Success(activities[index])
    }

    override suspend fun clearParticipantSignal(
        activityId: IturActivityId,
        userId: UserId,
    ): DataResult<IturActivity> {
        val index = activities.indexOfFirst { it.id == activityId }
        if (index < 0) return DataResult.NotFound(activityId.value)
        activities[index] = activities[index].copy(
            participantSignals = activities[index].participantSignals - userId,
        )
        return DataResult.Success(activities[index])
    }

    override suspend fun requestAttention(activityId: IturActivityId, userId: UserId) {
        attentionRequestCount.incrementAndGet()
        setParticipantSignal(activityId, userId, ParticipantSignal.NEEDS_HELP)
    }

    override suspend fun getBroadcastsSince(
        activityId: IturActivityId,
        since: Date?,
    ): List<Broadcast> = emptyList()
}

class ScenarioLocationRepository(
    private val activities: ScenarioActivityRepository,
) : LocationRepository {
    private val locations = mutableMapOf(
        TestFixtures.ONGOING_ACTIVITY_ID to TestFixtures.ongoingActivityLocations
            .associate { it.userId to it.location }
            .toMutableMap(),
    )

    var removeFailure: Throwable? = null
    val updateCount = AtomicInteger()
    val removeCount = AtomicInteger()

    override suspend fun getForActivity(
        activityId: IturActivityId,
    ): List<ParticipantLocation> {
        val activity = activities.activity(activityId) ?: return emptyList()
        return activity.participantIds.mapNotNull { userId ->
            locations[activityId]?.get(userId)?.let { location ->
                ParticipantLocation(
                    activityId = activityId,
                    userId = userId,
                    userName = "User ${userId.value}",
                    location = location,
                )
            }
        }
    }

    override suspend fun updateForParticipant(
        userId: UserId,
        activityId: IturActivityId,
        location: Location,
    ) {
        updateCount.incrementAndGet()
        locations.getOrPut(activityId) { mutableMapOf() }[userId] = location
    }

    override suspend fun removeForActivity(activityId: IturActivityId) {
        removeFailure?.let { throw it }
        removeCount.incrementAndGet()
        locations.remove(activityId)
    }

    override suspend fun removeForParticipant(userId: UserId, activityId: IturActivityId) {
        removeFailure?.let { throw it }
        removeCount.incrementAndGet()
        locations[activityId]?.remove(userId)
    }
}
