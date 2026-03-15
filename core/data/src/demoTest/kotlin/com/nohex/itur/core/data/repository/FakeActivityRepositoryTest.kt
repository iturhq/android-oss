/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.data.repository

import com.nohex.itur.core.domain.id.UserId
import com.nohex.itur.core.model.IturActivityStatus
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FakeActivityRepositoryTest {
    @Test
    fun fullRun() = runBlocking {
        val sut = FakeActivityRepository()

        val organizerId = UserId("rgnyza")
        val participantId = UserId("participant-1")

        assertTrue { sut.getActivities(ActivityFilter.ByOrganizer(organizerId)).isEmpty() }

        val testActivity = sut.createActivity(organizerId)
        assertFalse { sut.getActivities(ActivityFilter.ByOrganizer(organizerId)).isEmpty() }

        val activities = sut.getActivities(ActivityFilter.ById(testActivity.id))
        assertFalse { activities.isEmpty() }
        assertTrue { activities.contains(testActivity) }

        var activity = activities.find { it == testActivity }!!
        assertEquals(IturActivityStatus.DRAFT, activity.status)

        val newStatus = IturActivityStatus.ONGOING
        sut.updateActivityStatus(activity.id, newStatus)
        // Reload activity.
        activity = sut.getActivities(ActivityFilter.ById(testActivity.id)).first()
        assertEquals(newStatus, activity.status)

        assertTrue { activity.participantIds.isEmpty() }
        sut.addParticipant(activity.id, participantId)
        activity = sut.getActivities(ActivityFilter.ById(testActivity.id)).first()
        assertFalse { activity.participantIds.isEmpty() }
        assertTrue { activity.participantIds.contains(participantId) }

        sut.removeParticipant(activity.id, participantId)
        activity = sut.getActivities(ActivityFilter.ById(testActivity.id)).first()
        assertFalse { activity.participantIds.contains(participantId) }
        assertTrue { activity.participantIds.isEmpty() }
    }
}
