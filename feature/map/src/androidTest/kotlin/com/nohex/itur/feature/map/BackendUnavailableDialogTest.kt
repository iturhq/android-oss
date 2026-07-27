/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.feature.map

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nohex.itur.core.ui.theme.IturTheme
import com.nohex.itur.feature.map.ui.BackendUnavailableDialog
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BackendUnavailableDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun multipleFailuresCountdownAndActionsAreShown() {
        var retried = false
        var exited = false
        composeRule.setContent {
            IturTheme {
                BackendUnavailableDialog(
                    failingServiceNames = listOf(
                        "Firebase Authentication",
                        "Cloud Firestore",
                    ),
                    countdown = 5,
                    onRetryNow = { retried = true },
                    onExit = { exited = true },
                )
            }
        }

        composeRule.onNodeWithText(
            "Failed connection to Firebase Authentication, Cloud Firestore.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Retrying in 5s\u2026").assertIsDisplayed()
        composeRule.onNodeWithText("Retry now").performClick()
        composeRule.onNodeWithText("Exit").performClick()

        assertTrue(retried)
        assertTrue(exited)
    }
}
