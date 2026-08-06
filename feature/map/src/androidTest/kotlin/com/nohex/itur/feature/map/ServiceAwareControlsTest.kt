/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.feature.map

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.nohex.itur.core.ui.theme.IturTheme
import com.nohex.itur.feature.map.ui.components.map.IdleState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ServiceAwareControlsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun localEntryActionsRemainAvailableDuringAnOutage() {
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
                    externalActionsEnabled = false,
                )
            }
        }

        composeRule.onNodeWithTag("sign_in_fab").assertIsEnabled().performClick()
        composeRule.onNodeWithTag("join_activity_fab").assertIsEnabled().performClick()

        assertTrue(signedIn)
        assertTrue(joined)
    }
}
