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
import com.nohex.itur.feature.map.ui.components.map.PERSISTENT_MAP_NATIVE_VIEW_TAG
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer
import java.util.regex.Pattern
import javax.inject.Inject

/**
 * UC-01/02 cross the application-process boundary, so these two scenarios intentionally use
 * UIAutomator only for the Android-owned permission dialog and Compose semantics afterward.
 */
@HiltAndroidTest
class PermissionUseCasesTest {

    @Inject
    lateinit var locationClient: FakeLocationClient

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
        composeRule.onNodeWithTag("persistent_map_surface").assertIsDisplayed()
        composeRule.onNodeWithTag("location_permission_notice").assertDoesNotExist()
    }

    @Test
    fun uc02_denialKeepsMapAndGroupControlsThenGrantEnablesSelfLocation() {
        clickPermissionChoice(
            "Deny",
            "Don't allow",
            resourceName = "permission_deny_button",
        )

        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onAllNodesWithText(
                    "Location access is off",
                    substring = true,
                ).fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        composeRule.onNodeWithText("Location access is off", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("persistent_map_surface").assertIsDisplayed()
        composeRule.onNodeWithTag("join_activity_fab").assertIsDisplayed()
        composeRule.onNodeWithTag("sign_in_fab").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("start_activity_fab")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithTag("start_activity_fab").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("zoom_group_fab")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithTag("zoom_group_fab").assertIsDisplayed()
        composeRule.onNodeWithTag("recenter_fab").assertDoesNotExist()
        assertEquals(0, locationClient.requestCount.get())

        val mapBefore = checkNotNull(
            composeRule.activity.window.decorView
                .findViewWithTag<android.view.View>(PERSISTENT_MAP_NATIVE_VIEW_TAG),
        )
        composeRule.onNodeWithText("Enable location").performClick()
        clickPermissionChoice(
            "While using the app",
            "Allow only while using the app",
            "Allow",
            resourceName = "permission_allow_foreground_only_button",
        )

        composeRule.waitUntil(timeoutMillis = 10_000) {
            locationClient.requestCount.get() == 1
        }
        composeRule.onNodeWithTag("recenter_fab").assertIsDisplayed()
        composeRule.onNodeWithTag("location_permission_notice").assertDoesNotExist()
        val mapAfter = checkNotNull(
            composeRule.activity.window.decorView
                .findViewWithTag<android.view.View>(PERSISTENT_MAP_NATIVE_VIEW_TAG),
        )
        assertSame(mapBefore, mapAfter)
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
