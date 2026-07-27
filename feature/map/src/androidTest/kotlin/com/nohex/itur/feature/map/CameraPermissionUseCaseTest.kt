/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.feature.map

import android.Manifest
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

@HiltAndroidTest
class CameraPermissionUseCaseTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    @Before
    fun setUp() {
        hiltRule.inject()
        val packageName = instrumentation.targetContext.packageName
        instrumentation.uiAutomation.grantRuntimePermission(
            packageName,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
        instrumentation.uiAutomation.executeShellCommand(
            "pm revoke $packageName ${Manifest.permission.CAMERA}",
        ).close()
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
    fun uc10_deniedCameraPermissionKeepsIdleState() {
        composeRule.onNodeWithTag("join_activity_fab").performClick()
        val deny = device.wait(Until.findObject(By.textContains("Deny")), 5_000)
            ?: device.wait(Until.findObject(By.textContains("Don't allow")), 2_000)
        checkNotNull(deny) { "Android camera permission dialog did not expose a deny action" }
            .click()
        device.waitForIdle()

        composeRule.onNodeWithText(
            "Camera access is required",
            substring = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Scan an activity QR to join").assertDoesNotExist()
        composeRule.onNodeWithTag("join_activity_fab").assertIsDisplayed()
    }
}
