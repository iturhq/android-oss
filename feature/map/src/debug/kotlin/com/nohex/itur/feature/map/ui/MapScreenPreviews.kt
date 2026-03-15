/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.feature.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.nohex.itur.core.domain.id.IturActivityId
import com.nohex.itur.core.domain.id.UserId
import com.nohex.itur.core.domain.model.User
import com.nohex.itur.core.model.IturActivity
import com.nohex.itur.core.model.IturActivityStatus
import com.nohex.itur.core.model.Location
import com.nohex.itur.core.model.ParticipantLocation
import com.nohex.itur.feature.map.ui.components.map.ErrorState
import com.nohex.itur.feature.map.ui.components.map.IdleState
import com.nohex.itur.feature.map.ui.components.map.OngoingState

@Preview(
    showBackground = true,
    showSystemUi = true,
    device = "spec:width=540dp,height=960dp,orientation=portrait",
)
@Composable
fun IdleStatePreview() {
    IdleState(
        onSignInRequested = {},
        onSignOutRequested = {},
        onQRRequested = { },
        isSignedIn = false,
        onStartRequested = {},
    )
}

@Preview(
    showBackground = true,
    showSystemUi = true,
    device = "spec:width=540dp,height=960dp,orientation=portrait",
)
@Composable
fun OngoingStatePreview() {
    OngoingState(
        onQRRequested = { },
        onTrackUserRequested = { },
        activity = activity,
        organizer = organizer,
        participantIds = users.map { it.id },
        locations = locations,
        isOrganizer = true,
        onStopRequested = {},
        onTrackGroupRequested = {},
        onAttentionRequest = {},
    )
}

@Preview(
    name = "Landscape",
    showBackground = true,
    showSystemUi = true,
    device = "spec:width=960dp,height=540dp,orientation=landscape",
)
@Composable
fun MapErrorPreview() {
    ErrorState()
}

// //////////////////////////////////////////////////////////////////////////////////////////////////

private val organizerId = UserId("organizer")
private val organizer = User.RegisteredUser(
    organizerId,
    "R. Ganisa",
    "organizer@example.com",
)

private val participant1Id = UserId("user-1")
private val participant2Id = UserId("user-2")
private val users = listOf(
    User.RegisteredUser(participant1Id, "User Wan", "one@users.net"),
    User.RegisteredUser(participant2Id, "User Too", "two@users.net"),
)

private val activity = IturActivity(
    id = IturActivityId("00000000000000000001"),
    organizerId = UserId("someoneElse"),
    status = IturActivityStatus.ONGOING,
    participantIds = users.map { it.id },
)

private val locations = listOf(
    ParticipantLocation(activity.id, organizerId, organizer.name!!, Location(42.0, 1.0)),
    ParticipantLocation(
        activity.id,
        organizerId,
        users.find { it.id == participant1Id }?.name!!,
        Location(42.0001, 1.0001),
    ),
    ParticipantLocation(
        activity.id,
        organizerId,
        users.find { it.id == participant2Id }?.name!!,
        Location(41.9998, 1.0002),
    ),
)
