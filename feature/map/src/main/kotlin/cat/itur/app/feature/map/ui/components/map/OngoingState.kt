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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cat.itur.app.core.model.ParticipantSignal
import cat.itur.app.core.ui.IturIcons
import cat.itur.app.feature.map.ui.components.help.helpAnchor

internal data class OngoingState(
    val isOrganizer: Boolean,
    val selfSignal: ParticipantSignal? = null,
    val selfLocationAvailable: Boolean = true,
    val activityActionsEnabled: Boolean = true,
)

private object SafetySignalColors {
    const val DELAYED = 0xFFFFC107
    const val NEEDS_HELP = 0xFFD32F2F
    const val OK = 0xFF388E3C
}

/**
 * A composable for ongoing activities.
 *
 * It tracks the user's position.
 */
@Composable
internal fun OngoingState(
    actions: OngoingStateActions,
    presentation: OngoingState,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        FabSideColumn(horizontalAlignment = Alignment.Start, modifier = Modifier.padding(16.dp)) {
            HelpFABs(onHelpRequested = actions.onHelpRequested)
            TrackingFABs(
                onTrackUserRequested = actions.onTrackUserRequested,
                selfLocationAvailable = presentation.selfLocationAvailable,
            ) {
                FloatingActionButton(
                    onClick = actions.onTrackGroupRequested,
                    modifier = Modifier
                        .testTag("zoom_group_fab")
                        .helpAnchor("zoom_group_fab", "Zoom out to fit every participant on the map"),
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
                    actions = actions,
                    presentation = presentation,
                )
            }
        }
    }
}

@Composable
private fun OngoingActivityFABs(
    actions: OngoingStateActions,
    presentation: OngoingState,
) {
    // The QR button shows the QR sheet for scanning for organisers,
    // or the QR scanner for potential participants.
    if (presentation.isOrganizer) {
        FloatingActionButton(
            onClick = actions.onQrRequested,
            modifier = Modifier
                .testTag("show_qr_fab")
                .helpAnchor("show_qr_fab", "Show the QR code for others to join this activity"),
        ) {
            Icon(IturIcons.Join, contentDescription = "Show QR")
        }
    } else {
        ParticipantSafetyFABs(
            signal = presentation.selfSignal,
            enabled = presentation.activityActionsEnabled,
            onSignalRequested = actions.onParticipantSignalRequested,
        )
    }

    FloatingActionButton(
        onClick = {
            if (presentation.activityActionsEnabled) actions.onStopRequested()
        },
        modifier = Modifier
            .testTag("stop_activity_fab")
            .helpAnchor(
                "stop_activity_fab",
                if (presentation.isOrganizer) "Stop the activity for everyone" else "Leave the activity",
            )
            .serviceAvailability(presentation.activityActionsEnabled),
    ) {
        Icon(
            IturIcons.Stop,
            contentDescription = if (presentation.isOrganizer) "Stop activity" else "Exit activity",
        )
    }
}

@Composable
private fun ParticipantSafetyFABs(
    signal: ParticipantSignal?,
    enabled: Boolean,
    onSignalRequested: (ParticipantSignal?) -> Unit,
) {
    SafetySignalFAB(
        tag = "safety_delayed_fab",
        contentDescription = "I'm stopping; go ahead",
        color = Color(SafetySignalColors.DELAYED),
        enabled = enabled && signal == null,
        onClick = { onSignalRequested(ParticipantSignal.DELAYED) },
    )
    SafetySignalFAB(
        tag = "hail_organiser_fab",
        contentDescription = "I need help; converge on me",
        color = Color(SafetySignalColors.NEEDS_HELP),
        enabled = enabled && signal != ParticipantSignal.NEEDS_HELP,
        onClick = { onSignalRequested(ParticipantSignal.NEEDS_HELP) },
    )
    if (signal != null) {
        SafetySignalFAB(
            tag = "safety_ok_fab",
            contentDescription = "I'm okay",
            color = Color(SafetySignalColors.OK),
            enabled = enabled,
            onClick = { onSignalRequested(null) },
        )
    }
}

@Composable
private fun SafetySignalFAB(
    tag: String,
    contentDescription: String,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = Modifier
            .testTag(tag)
            .helpAnchor(tag, contentDescription)
            .serviceAvailability(enabled),
        containerColor = color,
    ) {
        Icon(IturIcons.Warning, contentDescription = contentDescription)
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
            onParticipantSignalRequested = {},
            onHelpRequested = {},
        ),
        presentation = OngoingState(isOrganizer = true),
    )
}
