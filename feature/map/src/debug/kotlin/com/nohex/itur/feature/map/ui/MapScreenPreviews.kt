/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.feature.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.nohex.itur.feature.map.ui.components.map.ErrorState
import com.nohex.itur.feature.map.ui.components.map.IdleState
import com.nohex.itur.feature.map.ui.components.map.OngoingState
import com.nohex.itur.feature.map.ui.components.map.OngoingStateActions

@Preview(
    showBackground = true,
    showSystemUi = true,
    device = "spec:width=540dp,height=960dp,orientation=portrait",
)
@Composable
fun IdleStatePreview() {
    IdleState(
        onStartRequested = {},
        onSignInRequested = {},
        onSignOutRequested = {},
        onQRRequested = {},
        onHelpRequested = {},
        isSignedIn = false,
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
