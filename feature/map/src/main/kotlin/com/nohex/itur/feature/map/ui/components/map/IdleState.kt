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
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nohex.itur.core.ui.IturIcons
import com.nohex.itur.core.ui.R
import com.nohex.itur.core.ui.theme.IturTheme
import com.nohex.itur.feature.map.ui.components.help.helpAnchor

/**
 * A composable to use when there is no ongoing activity.
 *
 * It shows a map initially trained on the current location.
 */
@Composable
fun IdleState(
    onStartRequested: () -> Unit,
    onSignInRequested: () -> Unit,
    onSignOutRequested: () -> Unit,
    onQRRequested: () -> Unit,
    onHelpRequested: () -> Unit,
    modifier: Modifier = Modifier,
    isSignedIn: Boolean,
    authenticationActionsEnabled: Boolean = true,
    activityActionsEnabled: Boolean = true,
) {
    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        Icon(
            painter = painterResource(R.drawable.core_ui_itur_overlay),
            tint = Color.Transparent,
            contentDescription = "Itur logo",
            modifier = Modifier
                .size(64.dp)
                .padding(8.dp),
        )

        FabSideColumn(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier.padding(16.dp),
        ) {
            HelpFABs(onHelpRequested = onHelpRequested)
        }

        FabSideColumn(horizontalAlignment = Alignment.End) {
            // User actions, top right.
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(16.dp),
            ) {
                UserFABs(
                    onSignInRequested,
                    onSignOutRequested,
                    isSignedIn,
                    authenticationActionsEnabled,
                )
            }

            // Activity actions.
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(16.dp),
            ) {
                ActivityFABs(
                    onStartRequested = onStartRequested,
                    isSignedIn = isSignedIn,
                    onQRRequested = onQRRequested,
                    activityActionsEnabled = activityActionsEnabled,
                )
            }
        }
    }
}

@Composable
private fun ActivityFABs(
    // A (signed-in) user requests the creation of an activity.
    onStartRequested: () -> Unit,
    // The QR button was pressed.
    onQRRequested: () -> Unit,
    // Whether the user is registered or anonymous.
    isSignedIn: Boolean,
    activityActionsEnabled: Boolean,
) {
    // The QR button shows the QR sheet for scanning for organisers,
    // or the QR scanner for potential participants.
    FloatingActionButton(
        onClick = onQRRequested,
        modifier = Modifier
            .testTag("join_activity_fab")
            .helpAnchor("join_activity_fab", "Join an activity by scanning its QR code"),
    ) {
        Icon(IturIcons.Join, contentDescription = "Join activity")
    }

    // Only signed-in users can start activities.
    if (isSignedIn) {
        FloatingActionButton(
            onClick = { if (activityActionsEnabled) onStartRequested() },
            modifier = Modifier
                .testTag("start_activity_fab")
                .helpAnchor("start_activity_fab", "Start a new activity")
                .serviceAvailability(activityActionsEnabled),
        ) {
            Icon(IturIcons.Add, contentDescription = "Start activity")
        }
    }
}

internal fun Modifier.serviceAvailability(enabled: Boolean): Modifier = if (enabled) this else serviceDisabled()

private fun Modifier.serviceDisabled(): Modifier = alpha(DISABLED_ACTION_ALPHA).semantics { disabled() }

private const val DISABLED_ACTION_ALPHA = 0.38f

@Preview(showBackground = true)
@Composable
private fun IdleMapPreview() {
    IturTheme {
        IdleState(
            onStartRequested = {},
            onSignInRequested = {},
            onSignOutRequested = {},
            onQRRequested = {},
            onHelpRequested = {},
            isSignedIn = true,
        )
    }
}
