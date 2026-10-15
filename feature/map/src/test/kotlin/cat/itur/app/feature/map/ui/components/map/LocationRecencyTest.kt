/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map.ui.components.map

import cat.itur.app.core.domain.id.IturActivityId
import cat.itur.app.core.domain.id.UserId
import cat.itur.app.core.model.Location
import cat.itur.app.core.model.ParticipantLocation
import cat.itur.app.core.model.ParticipantSignal
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocationRecencyTest {
    @Test
    fun `accessibility text announces name role and participant signal`() {
        val now = 50_000L
        val participant = location(now)

        val description = MapLibreViewInput(
            styleUrl = "https://example.invalid/style.json",
            isActivityOngoing = true,
            activityId = participant.activityId,
            locationPermissionGranted = true,
            currentUserId = participant.userId,
            organizerId = null,
            participantLocations = listOf(participant),
            participantSignals = mapOf(participant.userId to ParticipantSignal.NEEDS_HELP),
        ).accessibilityDescription(
            nowMillis = now,
        )

        assertTrue(description.contains(participant.userName))
        assertTrue(description.contains("self"))
        assertTrue(description.contains("needs-help"))
    }

    private val now = 100_000L

    @Test
    fun `location ages through current aging and stale tiers`() {
        assertEquals(LocationRecency.CURRENT, location(now - 14_999L).recency(now))
        assertEquals(LocationRecency.AGING, location(now - 15_000L).recency(now))
        assertEquals(LocationRecency.STALE, location(now - 30_000L).recency(now))
    }

    @Test
    fun `missing timestamps are unknown rather than fresh`() {
        val unknown = location(null)

        assertEquals(LocationRecency.UNKNOWN, unknown.recency(now))
        assertTrue(unknown.accessibleAge(now).contains("age is unknown"))
    }

    @Test
    fun `stale accessibility text announces both state and approximate age`() {
        val description = location(now - 42_000L).accessibleAge(now)

        assertTrue(description.contains("is stale"))
        assertTrue(description.contains("42 seconds ago"))
    }

    private fun location(recordedAtMillis: Long?) = ParticipantLocation(
        activityId = IturActivityId("activity000000000001"),
        userId = UserId("participant-1"),
        userName = "Ada",
        location = Location(41.38, 2.17),
        recordedAt = recordedAtMillis?.let(::Date),
    )
}
