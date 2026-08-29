/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map.ui.components.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cat.itur.app.core.ui.IturIcons
import cat.itur.app.feature.map.ui.components.help.helpAnchor

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
    authenticationActionsEnabled: Boolean,
    showSignOutOnMap: Boolean? = null,
) {
    val shouldShowSignOut = showSignOutOnMap
        ?: LocalContext.current.resources.getBoolean(
            cat.itur.app.feature.map.R.bool.feature_map_show_sign_out_on_map,
        )
    if (isSignedIn && shouldShowSignOut) {
        FloatingActionButton(
            onClick = onSignOutRequested,
            modifier = Modifier
                .testTag("sign_out_fab")
                .helpAnchor("sign_out_fab", "Sign out of your account"),
        ) {
            Icon(IturIcons.SignOut, contentDescription = "Sign out")
        }
    } else if (!isSignedIn) {
        FloatingActionButton(
            onClick = { if (authenticationActionsEnabled) onSignInRequested() },
            modifier = Modifier
                .testTag("sign_in_fab")
                .helpAnchor("sign_in_fab", "Sign in to start or manage an activity")
                .serviceAvailability(authenticationActionsEnabled),
        ) {
            Icon(IturIcons.SignIn, contentDescription = "Sign in")
        }
    }
}

@Composable
internal fun TrackingFABs(
    onTrackUserRequested: () -> Unit,
    onOrientationToggleRequested: () -> Unit,
    isDirectionOfTravel: Boolean,
    isUserTracking: Boolean,
    selfLocationAvailable: Boolean,
    content: @Composable () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FloatingActionButton(
            onClick = onOrientationToggleRequested,
            modifier = Modifier
                .testTag("map_orientation_fab")
                .helpAnchor(
                    "map_orientation_fab",
                    "Switch between north-up and direction-of-travel map views",
                ),
        ) {
            Icon(
                IturIcons.Orientation,
                contentDescription = if (isDirectionOfTravel) {
                    "Switch to north-up view"
                } else {
                    "Switch to direction-of-travel view"
                },
            )
        }

        content()

        if (selfLocationAvailable) {
            FloatingActionButton(
                onClick = onTrackUserRequested,
                modifier = Modifier
                    .testTag("recenter_fab")
                    .semantics { selected = isUserTracking }
                    .helpAnchor("recenter_fab", "Recenter the map on your own location"),
                containerColor = if (isUserTracking) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
                contentColor = if (isUserTracking) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                },
            ) {
                Icon(IturIcons.ZoomSelf, contentDescription = "Recenter")
            }
        }
    }
}

@Composable
internal fun HelpFABs(onHelpRequested: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FloatingActionButton(
            onClick = onHelpRequested,
            modifier = Modifier.testTag("help_fab"),
        ) {
            Icon(IturIcons.Help, contentDescription = "Get help")
        }
    }
}
