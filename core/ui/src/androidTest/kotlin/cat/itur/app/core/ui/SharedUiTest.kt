/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.core.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import cat.itur.app.core.ui.components.ActionButtonType
import cat.itur.app.core.ui.components.IturBackground
import cat.itur.app.core.ui.components.IturButton
import cat.itur.app.core.ui.components.IturProgressIndicator
import cat.itur.app.core.ui.theme.IturTheme
import org.junit.Rule
import org.junit.Test

class SharedUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun themesAndSharedComponentsExposeAccessibleContent() {
        composeRule.setContent {
            IturTheme(darkTheme = false, dynamicColor = false) {
                IturBackground {
                    MaterialTheme.colorScheme.primary
                    IturButton("Primary", type = ActionButtonType.PRIMARY, onClick = {})
                    IturButton("Secondary", type = ActionButtonType.SECONDARY, onClick = {})
                    IturButton("Warning", type = ActionButtonType.WARNING, onClick = {})
                    IturButton("Danger", type = ActionButtonType.DANGER, onClick = {})
                    IturProgressIndicator(label = "Loading")
                }
            }
        }

        listOf("Primary", "Secondary", "Warning", "Danger", "Loading").forEach {
            composeRule.onNodeWithText(it).assertIsDisplayed()
        }
    }

    @Test
    fun darkThemeAndStaticDynamicFallbackCompose() {
        composeRule.setContent {
            IturTheme(darkTheme = true, dynamicColor = false) {
                MaterialTheme.colorScheme.primary
            }
            IturTheme(darkTheme = false, dynamicColor = true) {
                MaterialTheme.colorScheme.primary
            }
        }
    }
}
