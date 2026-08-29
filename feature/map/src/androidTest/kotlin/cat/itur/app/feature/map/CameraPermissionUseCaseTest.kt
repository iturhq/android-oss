/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import cat.itur.app.core.ui.theme.IturTheme
import cat.itur.app.feature.map.ui.MapScreen
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
    val locationPermissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    @get:Rule(order = 2)
    val notificationPermissionRule = NotificationPermissionRule()

    @get:Rule(order = 3)
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
                MapScreen(
                    locationPermissionCheck = { true },
                    cameraPermissionRequest = { onResult -> onResult(false) },
                )
            }
        }
    }

    @Test
    fun uc10_deniedCameraPermissionKeepsIdleState() {
        val cameraPermission = ContextCompat.checkSelfPermission(
            ApplicationProvider.getApplicationContext(),
            Manifest.permission.CAMERA,
        )
        check(cameraPermission == PackageManager.PERMISSION_DENIED) {
            "Camera permission must start denied, but was $cameraPermission"
        }
        composeRule.onNodeWithTag("join_activity_fab").performClick()
        composeRule.waitForIdle()
        SystemClock.sleep(500)
        check(clickPermissionDeny()) {
            "Android camera permission dialog did not expose a deny action"
        }
        device.waitForIdle()
        instrumentation.waitForIdleSync()

        composeRule.onNodeWithText(
            "Camera access is required",
            substring = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Scan an activity QR to join").assertDoesNotExist()
        composeRule.onNodeWithTag("join_activity_fab").assertIsDisplayed()
    }

    private fun clickPermissionDeny(): Boolean {
        val legacyDenySelectors = listOf(
            UiSelector().resourceIdMatches(".*:id/permission_deny_button"),
            UiSelector().textMatches("(?i)deny|don't allow"),
        )
        legacyDenySelectors.forEach { selector ->
            val denyButton = device.findObject(selector)
            if (denyButton.waitForExists(2_500) && runCatching { denyButton.click() }.isSuccess) {
                return true
            }
        }

        val controllerPackages = listOf(
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
            "com.android.packageinstaller",
        )
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (SystemClock.uptimeMillis() < deadline) {
            val nodes = controllerPackages.flatMap { packageName ->
                device.findObjects(By.pkg(packageName))
            } + device.findObjects(By.clickable(true))
            nodes.forEach { node ->
                val isDeny = runCatching {
                    node.resourceName?.endsWith(":id/permission_deny_button") == true ||
                        node.text.equals("Deny", ignoreCase = true) ||
                        node.text.equals("Don't allow", ignoreCase = true)
                }.getOrDefault(false)
                if (isDeny && runCatching { node.click() }.isSuccess) return true
            }
            SystemClock.sleep(100)
        }
        return false
    }
}
