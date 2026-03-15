/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.feature.map.ui.components.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.nohex.itur.core.ui.IturIcons

@Composable
internal fun UserFABs(
    onSignInRequested: () -> Unit,
    onSignOutRequested: () -> Unit,
    isSignedIn: Boolean,
) {
    if (isSignedIn) {
        ExtendedFloatingActionButton(
            expanded = false,
            onClick = onSignOutRequested,
            modifier = Modifier.testTag("sign_out_fab"),
            icon = {
                Icon(IturIcons.SignOut, contentDescription = "Sign out")
            },
            text = {
                Text(text = "Sign out")
            },
        )
    } else {
        ExtendedFloatingActionButton(
            onClick = onSignInRequested,
            modifier = Modifier.testTag("sign_in_fab"),
            icon = {
                Icon(IturIcons.SignIn, contentDescription = "Sign in")
            },
            text = {
                Text(text = "Sign in")
            },
        )
    }
}

@Composable
internal fun TrackingFABs(
    onTrackUserRequested: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        content()

        FloatingActionButton(
            onClick = onTrackUserRequested,
            modifier = Modifier.testTag("recenter_fab"),
        ) {
            Icon(IturIcons.ZoomSelf, contentDescription = "Recenter")
        }
    }
}

@Composable
internal fun HelpFABs() {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FloatingActionButton(
            onClick = { /* TODO: Display help overlay */ },
            modifier = Modifier.testTag("help_fab"),
        ) {
            Icon(IturIcons.Help, contentDescription = "Get help")
        }
    }
}
