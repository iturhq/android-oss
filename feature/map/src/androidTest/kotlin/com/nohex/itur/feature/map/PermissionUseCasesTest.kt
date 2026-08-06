/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.feature.map

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.nohex.itur.core.ui.theme.IturTheme
import com.nohex.itur.feature.map.ui.MapScreen
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer
import java.util.regex.Pattern

/**
 * UC-01/02 cross the application-process boundary, so these two scenarios intentionally use
 * UIAutomator only for the Android-owned permission dialog and Compose semantics afterward.
 */
@HiltAndroidTest
class PermissionUseCasesTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    @Before
    fun setUp() {
        hiltRule.inject()
        instrumentation.runOnMainSync {
            MapLibre.getInstance(
                ApplicationProvider.getApplicationContext<Context>(),
                "",
                WellKnownTileServer.MapLibre,
            )
        }
        composeRule.setContent {
            IturTheme {
                MapScreen()
            }
        }
    }

    @Test
    fun uc01_grantingLocationPermissionShowsIdleMapControls() {
        clickPermissionChoice(
            "While using the app",
            "Allow only while using the app",
            "Allow",
            resourceName = "permission_allow_foreground_only_button",
        )

        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onAllNodesWithTag("join_activity_fab")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("join_activity_fab").assertIsDisplayed()
        composeRule.onNodeWithTag("sign_in_fab").assertIsDisplayed()
    }

    @Test
    fun uc02_denyingLocationPermissionShowsGuidanceWithoutMapControls() {
        clickPermissionChoice(
            "Deny",
            "Don't allow",
            resourceName = "permission_deny_button",
        )

        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onAllNodesWithText(
                    "Location permission is required",
                    substring = true,
                ).fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        composeRule.onNodeWithText("Location permission is required", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Itur").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Allow location access to show the map and share your position during an activity.",
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("join_activity_fab").assertDoesNotExist()
    }

    private fun clickPermissionChoice(vararg labels: String, resourceName: String) {
        val permissionControl = By.res(
            Pattern.compile(".*:id/${Pattern.quote(resourceName)}"),
        )
        val permissionController = device.wait(
            Until.findObject(permissionControl),
            5_000,
        )
        val selector = permissionController ?: labels.asSequence()
            .mapNotNull { label ->
                device.wait(Until.findObject(By.textContains(label)), 2_000)
            }
            .firstOrNull()
        checkNotNull(selector) {
            "Android location permission dialog did not expose any of ${labels.toList()}"
        }.click()
        device.wait(
            Until.gone(permissionControl),
            5_000,
        )
        instrumentation.waitForIdleSync()
    }
}
