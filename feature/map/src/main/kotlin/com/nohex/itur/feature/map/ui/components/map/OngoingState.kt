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
import com.nohex.itur.core.ui.IturIcons

/**
 * A composable for ongoing activities.
 *
 * It tracks the user's position.
 */
@Composable
internal fun OngoingState(
    actions: OngoingStateActions,
    isOrganizer: Boolean,
    modifier: Modifier = Modifier,
    activityActionsEnabled: Boolean = true,
) {
    Box(modifier = modifier.fillMaxSize()) {
        FabSideColumn(horizontalAlignment = Alignment.Start, modifier = Modifier.padding(16.dp)) {
            HelpFABs(onHelpRequested = actions.onHelpRequested)
            TrackingFABs(
                onTrackUserRequested = actions.onTrackUserRequested,
            ) {
                FloatingActionButton(
                    onClick = actions.onTrackGroupRequested,
                    modifier = Modifier.testTag("zoom_group_fab"),
                ) {
                    Icon(IturIcons.ZoomAll, contentDescription = "Track group")
                }
            }
        }
        // End FABs
        FabSideColumn(horizontalAlignment = Alignment.End, modifier = Modifier.padding(16.dp)) {
            // User actions not available during an activity.
            // Column left for layout.
            Column { }

            // Activity actions.
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OngoingActivityFABs(
                    onStopRequested = actions.onStopRequested,
                    onQRRequested = actions.onQrRequested,
                    isOrganizer = isOrganizer,
                    onAttentionRequest = actions.onAttentionRequest,
                    activityActionsEnabled = activityActionsEnabled,
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
    activityActionsEnabled: Boolean,
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
            onClick = { if (activityActionsEnabled) onAttentionRequest() },
            modifier = Modifier.testTag("hail_organiser_fab")
                .serviceAvailability(activityActionsEnabled),
        ) {
            Icon(IturIcons.Warning, contentDescription = "Hail organiser")
        }
    }

    FloatingActionButton(
        onClick = { if (activityActionsEnabled) onStopRequested() },
        modifier = Modifier.testTag("stop_activity_fab")
            .serviceAvailability(activityActionsEnabled),
    ) {
        Icon(
            IturIcons.Stop,
            contentDescription = if (isOrganizer) "Stop activity" else "Exit activity",
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun OrganizerOngoingStatePreview() {
    OngoingState(
        actions = OngoingStateActions(
            onStopRequested = {},
            onQrRequested = {},
            onTrackUserRequested = {},
            onTrackGroupRequested = {},
            onAttentionRequest = {},
            onHelpRequested = {},
        ),
        isOrganizer = true,
    )
}
