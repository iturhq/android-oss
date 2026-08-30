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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class PointerBearingTest {
    private val now = 2_000_000L

    @Test
    fun `fresh accurate bearing rotates the actual rendered remote feature`() {
        val location = Location(
            latitude = 41.0,
            longitude = 2.0,
            providerTimestampMillis = now - 1_000,
            bearingDegrees = 123f,
            bearingAccuracyDegrees = 4f,
        )

        assertEquals(123f, pointerBearingRotation(location, now))
        val feature = feature(location, ParticipantSignal.DELAYED)
        assertEquals(123f, feature.getNumberProperty("bearing").toFloat())
        assertEquals("marker-other-delayed-directional", feature.getStringProperty("marker"))
        assertEquals(0.6f, feature.getNumberProperty("opacity").toFloat())
    }

    @Test
    fun `absent stale future or unreliable bearing renders a signal-aware neutral feature`() {
        val valid = Location(
            latitude = 41.0,
            longitude = 2.0,
            providerTimestampMillis = now,
            bearingDegrees = 90f,
        )

        listOf(
            valid.copy(bearingDegrees = null),
            valid.copy(providerTimestampMillis = null),
            valid.copy(providerTimestampMillis = now - MAX_POINTER_BEARING_AGE_MILLIS - 1),
            valid.copy(providerTimestampMillis = now + 1),
            valid.copy(bearingDegrees = Float.NaN),
            valid.copy(bearingDegrees = -1f),
            valid.copy(bearingDegrees = 361f),
            valid.copy(bearingAccuracyDegrees = Float.NaN),
            valid.copy(bearingAccuracyDegrees = MAX_POINTER_BEARING_ACCURACY_DEGREES + 1),
        ).forEach { location ->
            assertNull(pointerBearingRotation(location, now))
            val feature = feature(location, ParticipantSignal.NEEDS_HELP)
            assertFalse(feature.hasProperty("bearing"))
            assertEquals("marker-other-needs-help-neutral", feature.getStringProperty("marker"))
        }
    }

    @Test
    fun `full turn bearing is normalized to zero`() {
        val location = Location(
            latitude = 41.0,
            longitude = 2.0,
            providerTimestampMillis = now,
            bearingDegrees = 360f,
        )

        assertEquals(0f, pointerBearingRotation(location, now))
    }

    private fun feature(location: Location, signal: ParticipantSignal?) = remotePointerFeature(
        participantLocation = ParticipantLocation(
            activityId = IturActivityId("TestActivity00000001"),
            userId = UserId("remote-user"),
            userName = "Remote",
            location = location,
        ),
        role = PointerRole.OTHER,
        signal = signal,
        opacity = 0.6f,
        nowMillis = now,
    )
}
