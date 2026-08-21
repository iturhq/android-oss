/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map.ui.components.map

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cat.itur.app.core.ui.IturIcons
import cat.itur.app.feature.map.R
import cat.itur.app.feature.map.ui.components.help.helpAnchor

/**
 * A composable for ongoing activities.
 *
 * It tracks the user's position.
 */
@Composable
internal fun OngoingState(
    actions: OngoingStateActions,
    isOrganizer: Boolean,
    selfLocationAvailable: Boolean = true,
    modifier: Modifier = Modifier,
    activityActionsEnabled: Boolean = true,
) {
    Box(modifier = modifier.fillMaxSize()) {
        FabSideColumn(horizontalAlignment = Alignment.Start, modifier = Modifier.padding(16.dp)) {
            HelpFABs(onHelpRequested = actions.onHelpRequested)
            TrackingFABs(
                onTrackUserRequested = actions.onTrackUserRequested,
                selfLocationAvailable = selfLocationAvailable,
            ) {
                FloatingActionButton(
                    onClick = actions.onTrackGroupRequested,
                    modifier = Modifier
                        .testTag("zoom_group_fab")
                        .helpAnchor(
                            "zoom_group_fab",
                            stringResource(R.string.feature_map_help_track_group),
                        ),
                ) {
                    Icon(
                        IturIcons.ZoomAll,
                        contentDescription = stringResource(R.string.feature_map_track_group),
                    )
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
            modifier = Modifier
                .testTag("show_qr_fab")
                .helpAnchor("show_qr_fab", stringResource(R.string.feature_map_help_show_qr)),
        ) {
            Icon(IturIcons.Join, contentDescription = stringResource(R.string.feature_map_show_qr))
        }
    } else {
        FloatingActionButton(
            onClick = { if (activityActionsEnabled) onAttentionRequest() },
            modifier = Modifier
                .testTag("hail_organiser_fab")
                .helpAnchor(
                    "hail_organiser_fab",
                    stringResource(R.string.feature_map_help_hail_organiser),
                )
                .serviceAvailability(activityActionsEnabled),
        ) {
            Icon(
                IturIcons.Warning,
                contentDescription = stringResource(R.string.feature_map_hail_organiser),
            )
        }
    }

    FloatingActionButton(
        onClick = { if (activityActionsEnabled) onStopRequested() },
        modifier = Modifier
            .testTag("stop_activity_fab")
            .helpAnchor(
                "stop_activity_fab",
                if (isOrganizer) {
                    stringResource(R.string.feature_map_help_stop_activity)
                } else {
                    stringResource(R.string.feature_map_help_leave_activity)
                },
            )
            .serviceAvailability(activityActionsEnabled),
    ) {
        Icon(
            IturIcons.Stop,
            contentDescription = if (isOrganizer) {
                stringResource(R.string.feature_map_stop_activity)
            } else {
                stringResource(R.string.feature_map_exit_activity)
            },
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
        selfLocationAvailable = true,
    )
}
