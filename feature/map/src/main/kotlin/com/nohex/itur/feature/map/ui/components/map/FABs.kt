/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.feature.map.ui.components.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.nohex.itur.core.ui.IturIcons

/**
 * One full-height, edge-aligned FAB column within a map state's enclosing `Box`.
 *
 * `IdleState` and `OngoingState` each place one or two of these side by side (as siblings in
 * their own `Box(fillMaxSize)`) instead of each declaring their own
 * `Column(fillMaxSize, horizontalAlignment, verticalArrangement = SpaceBetween)`; tracked as
 * `AOSS-2A3E`. [modifier] carries whatever padding each call site needs -- deliberately not
 * baked in here, since `IdleState`'s pads each inner FAB group individually while
 * `OngoingState` pads the whole column, and unifying that too would change either's layout.
 */
@Composable
internal fun FabSideColumn(
    horizontalAlignment: Alignment.Horizontal,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = Arrangement.SpaceBetween,
        content = content,
    )
}

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
            // Help overlay not implemented yet; tracked as AOSS-A49A.
            onClick = {},
            modifier = Modifier.testTag("help_fab"),
        ) {
            Icon(IturIcons.Help, contentDescription = "Get help")
        }
    }
}
