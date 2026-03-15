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
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nohex.itur.core.ui.IturIcons
import com.nohex.itur.core.ui.R
import com.nohex.itur.core.ui.theme.IturTheme

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
    modifier: Modifier = Modifier,
    isSignedIn: Boolean,
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

        // TODO: Rearrange and encapsulate FABs.
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // User actions, top right.
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(16.dp),
            ) {
                UserFABs(onSignInRequested, onSignOutRequested, isSignedIn)
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
) {
    // The QR button shows the QR sheet for scanning for organisers,
    // or the QR scanner for potential participants.
    ExtendedFloatingActionButton(
        onClick = onQRRequested,
        modifier = Modifier.testTag("join_activity_fab"),
        text = {
            Text(text = "Join an activity")
        },
        icon = {
            Icon(IturIcons.Join, contentDescription = "Join activity")
        },
    )

    // Only signed-in users can start activities.
    if (isSignedIn) {
        ExtendedFloatingActionButton(
            onClick = onStartRequested,
            modifier = Modifier.testTag("start_activity_fab"),
            text = {
                Text(text = "Start an activity")
            },
            icon = {
                Icon(IturIcons.Add, contentDescription = "Start activity")
            },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun IdleMapPreview() {
    IturTheme {
        IdleState(
            onStartRequested = {},
            onSignInRequested = {},
            onSignOutRequested = {},
            onQRRequested = {},
            isSignedIn = true,
        )
    }
}
