/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.model

import com.nohex.itur.core.domain.id.IturActivityId
import com.nohex.itur.core.domain.id.UserId
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val ORGANIZER_ID = UserId("organizer1")
private val PARTICIPANT_ID = UserId("participant1")
private val ACTIVITY = IturActivity(
    id = IturActivityId("TestActivity00000001"),
    status = IturActivityStatus.ONGOING,
    organizerId = ORGANIZER_ID,
    participantIds = listOf(ORGANIZER_ID, PARTICIPANT_ID),
)

class IturActivityExtensionsTest {

    @Test
    fun `GIVEN an activity WHEN checking isOrganizer with the organizer ID THEN returns true`() {
        assertTrue(ACTIVITY.isOrganizer(ORGANIZER_ID))
    }

    @Test
    fun `GIVEN an activity WHEN checking isOrganizer with a participant ID THEN returns false`() {
        assertFalse(ACTIVITY.isOrganizer(PARTICIPANT_ID))
    }

    @Test
    fun `GIVEN an activity WHEN checking isOrganizer with an unknown ID THEN returns false`() {
        assertFalse(ACTIVITY.isOrganizer(UserId("unknown")))
    }
}
