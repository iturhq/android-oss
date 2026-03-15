/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.feature.map.ui.components.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nohex.itur.core.domain.id.UserId
import com.nohex.itur.core.domain.model.User
import com.nohex.itur.core.model.IturActivity
import com.nohex.itur.core.model.ParticipantLocation
import com.nohex.itur.core.ui.IturIcons

/**
 * A composable for ongoing activities.
 *
 * It tracks the user's position.
 */
@Composable
internal fun OngoingState(
    activity: IturActivity,
    organizer: User,
    participantIds: List<UserId>,
    locations: List<ParticipantLocation>,
    isOrganizer: Boolean,
    onStopRequested: () -> Unit,
    onQRRequested: () -> Unit,
    onTrackUserRequested: () -> Unit,
    onTrackGroupRequested: () -> Unit,
    onAttentionRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        // TODO: Rearrange and encapsulate FABs.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            HelpFABs()
            TrackingFABs(
                onTrackUserRequested = onTrackUserRequested,
            ) {
                FloatingActionButton(
                    onClick = onTrackGroupRequested,
                    modifier = Modifier.testTag("zoom_group_fab"),
                ) {
                    Icon(IturIcons.ZoomAll, contentDescription = "Track group")
                }
            }
        }
        // End FABs
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // User actions not available during an activity.
            // Column left for layout.
            Column { }

            // Activity actions.
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OngoingActivityFABs(
                    onStopRequested = onStopRequested,
                    onQRRequested = onQRRequested,
                    isOrganizer = isOrganizer,
                    onAttentionRequest = onAttentionRequest,
                )
            }
        }
    }
}

@Composable
private fun OngoingActivityFABs(
    onStopRequested: () -> Unit,
    onAttentionRequest: () -> Unit,
    onQRRequested: () -> Unit,
    // Whether the user is the organiser.
    isOrganizer: Boolean,
) {
    // The QR button shows the QR sheet for scanning for organisers,
    // or the QR scanner for potential participants.
    if (isOrganizer) {
        FloatingActionButton(
            onClick = onQRRequested,
            modifier = Modifier.testTag("show_qr_fab"),
        ) {
            Icon(IturIcons.Join, contentDescription = "Show QR")
        }
    } else {
        FloatingActionButton(
            onClick = onAttentionRequest,
            modifier = Modifier.testTag("hail_organiser_fab"),
        ) {
            Icon(IturIcons.Warning, contentDescription = "Hail organiser")
        }
    }

    FloatingActionButton(
        onClick = onStopRequested,
        modifier = Modifier.testTag("stop_activity_fab"),
    ) {
        Icon(IturIcons.Stop, contentDescription = "Stop activity")
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun OrganizerOngoingStatePreview() {
    OngoingState(
        activity = TODO(),
        organizer = User.AnonymousUser(UserId("preview-user")),
        participantIds = listOf(),
        locations = listOf(),
        isOrganizer = true,
        onStopRequested = {},
        onQRRequested = {},
        onTrackUserRequested = {},
        onTrackGroupRequested = {},
        onAttentionRequest = {},
    )
}
