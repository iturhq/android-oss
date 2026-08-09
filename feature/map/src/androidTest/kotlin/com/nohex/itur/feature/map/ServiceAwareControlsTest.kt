/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.feature.map

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.nohex.itur.core.ui.theme.IturTheme
import com.nohex.itur.feature.map.ui.components.map.IdleState
import com.nohex.itur.feature.map.ui.components.map.OngoingState
import com.nohex.itur.feature.map.ui.components.map.OngoingStateActions
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ServiceAwareControlsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun disabledAuthenticationActionsKeepJoinUsable() {
        var signedIn = false
        var joined = false
        composeRule.setContent {
            IturTheme {
                IdleState(
                    onStartRequested = {},
                    onSignInRequested = { signedIn = true },
                    onSignOutRequested = {},
                    onQRRequested = { joined = true },
                    isSignedIn = false,
                    authenticationActionsEnabled = false,
                    activityActionsEnabled = true,
                )
            }
        }

        composeRule.onNodeWithTag("sign_in_fab").assertIsNotEnabled().performClick()
        composeRule.onNodeWithTag("join_activity_fab").assertIsEnabled().performClick()

        assertFalse(signedIn)
        assertTrue(joined)
    }

    @Test
    fun disabledActivityActionsKeepJoinUsable() {
        var started = false
        var joined = false
        composeRule.setContent {
            IturTheme {
                IdleState(
                    onStartRequested = { started = true },
                    onSignInRequested = {},
                    onSignOutRequested = {},
                    onQRRequested = { joined = true },
                    isSignedIn = true,
                    authenticationActionsEnabled = true,
                    activityActionsEnabled = false,
                )
            }
        }

        composeRule.onNodeWithTag("start_activity_fab").assertIsNotEnabled().performClick()
        composeRule.onNodeWithTag("join_activity_fab").assertIsEnabled().performClick()

        assertFalse(started)
        assertTrue(joined)
    }

    @Test
    fun disabledActivityActionsKeepLocalOngoingControlsUsable() {
        var stopped = false
        var tracked = false
        composeRule.setContent {
            IturTheme {
                OngoingState(
                    actions = OngoingStateActions(
                        onStopRequested = { stopped = true },
                        onQrRequested = {},
                        onTrackUserRequested = { tracked = true },
                        onTrackGroupRequested = {},
                        onAttentionRequest = {},
                        onHelpRequested = {},
                    ),
                    isOrganizer = false,
                    activityActionsEnabled = false,
                )
            }
        }

        composeRule.onNodeWithTag("stop_activity_fab").assertIsNotEnabled().performClick()
        composeRule.onNodeWithTag("recenter_fab").assertIsEnabled().performClick()

        assertFalse(stopped)
        assertTrue(tracked)
    }
}
