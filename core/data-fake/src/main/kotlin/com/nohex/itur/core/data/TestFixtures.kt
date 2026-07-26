/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.data

import com.nohex.itur.core.domain.id.IturActivityId
import com.nohex.itur.core.domain.id.UserId
import com.nohex.itur.core.model.IturActivity
import com.nohex.itur.core.model.IturActivityStatus
import com.nohex.itur.core.model.Location
import com.nohex.itur.core.model.ParticipantLocation

/**
 * Deterministic test data shared between demo fake repositories and androidTest setup methods.
 *
 * User IDs match those defined in [com.nohex.itur.core.data.repository.FakeUserRepository].
 */
object TestFixtures {
    // User IDs – must match FakeUserRepository's users list.
    val ORGANIZER_ID = UserId("2") // William Henry Harrisson (RegisteredUser)
    val PARTICIPANT_1_ID = UserId("1") // AnonymousUser
    val PARTICIPANT_2_ID = UserId("4") // James Iredell (RegisteredUser)

    // Activity IDs (must be exactly 20 alphanumeric characters).
    val ONGOING_ACTIVITY_ID = IturActivityId("fixtureOngoingAct001")
    val DRAFT_ACTIVITY_ID = IturActivityId("fixtureDraftAct00001")

    // Pre-built activities.
    val ongoingActivity = IturActivity(
        id = ONGOING_ACTIVITY_ID,
        status = IturActivityStatus.ONGOING,
        organizerId = ORGANIZER_ID,
        participantIds = listOf(ORGANIZER_ID, PARTICIPANT_1_ID, PARTICIPANT_2_ID),
    )

    val draftActivity = IturActivity(
        id = DRAFT_ACTIVITY_ID,
        status = IturActivityStatus.DRAFT,
        organizerId = ORGANIZER_ID,
        participantIds = listOf(ORGANIZER_ID),
    )

    /** All activities to seed into [com.nohex.itur.core.data.repository.FakeActivityRepository]. */
    val activities = listOf(ongoingActivity, draftActivity)

    // Deterministic locations near Greenwich Park (51.4792842, 0.0).
    val ORGANIZER_LOCATION = Location(latitude = 51.4793, longitude = 0.0001)
    val PARTICIPANT_1_LOCATION = Location(latitude = 51.4795, longitude = -0.0003)
    val PARTICIPANT_2_LOCATION = Location(latitude = 51.4790, longitude = 0.0005)

    /** Pre-built participant locations for the ongoing activity. */
    val ongoingActivityLocations = listOf(
        ParticipantLocation(
            activityId = ONGOING_ACTIVITY_ID,
            userId = ORGANIZER_ID,
            location = ORGANIZER_LOCATION,
            userName = "William Henry Harrisson",
        ),
        ParticipantLocation(
            activityId = ONGOING_ACTIVITY_ID,
            userId = PARTICIPANT_1_ID,
            location = PARTICIPANT_1_LOCATION,
            userName = "User 1",
        ),
        ParticipantLocation(
            activityId = ONGOING_ACTIVITY_ID,
            userId = PARTICIPANT_2_ID,
            location = PARTICIPANT_2_LOCATION,
            userName = "James Iredell",
        ),
    )
}
