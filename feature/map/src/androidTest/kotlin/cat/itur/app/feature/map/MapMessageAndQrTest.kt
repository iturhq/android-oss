/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import cat.itur.app.core.ui.theme.IturTheme
import cat.itur.app.feature.map.ui.components.qrdisplay.QRImage
import cat.itur.app.feature.map.ui.localizedMapMessage
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class MapMessageAndQrTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    @Test
    fun localizedMessagesCoverStaticDynamicAndBackendFallbacks() {
        val messages = listOf(
            "Failed to start an activity" to "Failed to start an activity",
            "You're already in an activity -- leave it first" to "You’re already in an activity — leave it first",
            "This activity is full." to "This activity is full.",
            "Activity start limit reached." to "The activity start limit has been reached.",
            "You are no longer participating in an activity" to "You are no longer participating in an activity",
            "The ongoing activity could not be resumed." to "The ongoing activity could not be resumed.",
            "No Google account is available. Add an account and try again." to "No Google account is available. Add an account and try again.",
            "Sign-in isn't configured for this app." to "Sign-in isn’t configured for this app.",
            "Sign-in is temporarily unavailable. Check your connection and try again." to "Sign-in is temporarily unavailable. Check your connection and try again.",
            "Sign-in couldn't be completed. Try again." to "Sign-in couldn’t be completed. Try again.",
        )
        composeRule.setContent {
            IturTheme {
                Column {
                    Text(localizedMapMessage("Activity abc not found"), Modifier.testTag("message"))
                    messages.forEach { (message, _) -> Text(localizedMapMessage(message)) }
                    Text(localizedMapMessage("provider detail"))
                }
            }
        }
        composeRule.onNodeWithTag("message").assertIsDisplayed()
        composeRule.onNodeWithText("Activity abc not found").assertIsDisplayed()
        messages.forEach { (_, expected) ->
            composeRule.onNodeWithText(expected).assertIsDisplayed()
        }
        composeRule.onNodeWithText("provider detail").assertIsDisplayed()
    }

    @Test
    fun qrImageShowsFailureForInvalidDataAndRendersValidData() {
        composeRule.setContent {
            IturTheme {
                Column {
                    QRImage("not a URL")
                    QRImage("https://itur.cat/activities/test")
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Could not generate a QR code").assertIsDisplayed()

        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("activity_qr_image").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("activity_qr_image").assertIsDisplayed()
    }
}
