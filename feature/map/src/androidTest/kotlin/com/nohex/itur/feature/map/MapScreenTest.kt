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
import androidx.test.rule.GrantPermissionRule
import com.nohex.itur.core.ui.theme.IturTheme
import com.nohex.itur.feature.map.ui.MapScreen
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer

/**
 * Instrumented tests for [MapScreen], exercising it through the demo flavor's real Hilt graph
 * with [FakeLocationClient] (via [TestLocationModule]) standing in for GPS hardware, and the
 * demo flavor's own `core:data-fake` repositories standing in for Firebase. Tracked as
 * `AOSS-45FE`.
 */
@HiltAndroidTest
class MapScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    // Grant upfront so the system permission dialogs MapScreen requests on launch don't cover
    // the UI and block Compose test interactions. POST_NOTIFICATIONS is deliberately not
    // requested here: it doesn't exist as a runtime permission before API 33, and MapScreen
    // only asks for it on Build.VERSION.SDK_INT >= TIRAMISU in the first place.
    @get:Rule(order = 2)
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.CAMERA,
    )

    @Before
    fun setUp() {
        hiltRule.inject()
        // Instrumented library tests run under HiltTestApplication rather than IturApplication,
        // so repeat the production process-level MapLibre initialization before creating MapView.
        MapLibre.getInstance(
            ApplicationProvider.getApplicationContext<Context>(),
            "",
            WellKnownTileServer.MapLibre,
        )
        composeRule.setContent {
            IturTheme {
                MapScreen()
            }
        }
    }

    @Test
    fun anonymousUserSeesJoinAndSignInButNotStart() {
        composeRule.onNodeWithTag("join_activity_fab").assertIsDisplayed()
        composeRule.onNodeWithTag("sign_in_fab").assertIsDisplayed()
        composeRule.onNodeWithTag("start_activity_fab").assertDoesNotExist()
    }

    @Test
    fun signingInRevealsStartActivity() {
        composeRule.onNodeWithTag("sign_in_fab").performClick()

        composeRule.onNodeWithTag("sign_out_fab").assertIsDisplayed()
        composeRule.onNodeWithTag("start_activity_fab").assertIsDisplayed()
    }

    @Test
    fun startingAnActivityShowsOngoingControlsAsOrganizer() {
        composeRule.onNodeWithTag("sign_in_fab").performClick()
        composeRule.onNodeWithTag("start_activity_fab").performClick()

        composeRule.onNodeWithTag("recenter_fab").assertIsDisplayed()
        composeRule.onNodeWithTag("zoom_group_fab").assertIsDisplayed()
        composeRule.onNodeWithTag("help_fab").assertIsDisplayed()
        // The activity's creator is its organiser, so they see "show QR", not "hail organiser".
        composeRule.onNodeWithTag("show_qr_fab").assertIsDisplayed()
        composeRule.onNodeWithTag("hail_organiser_fab").assertDoesNotExist()
        composeRule.onNodeWithTag("stop_activity_fab").assertIsDisplayed()
    }

    @Test
    fun helpButtonExplainsTheOrganizerControls() {
        composeRule.onNodeWithTag("sign_in_fab").performClick()
        composeRule.onNodeWithTag("start_activity_fab").performClick()

        composeRule.onNodeWithTag("help_fab").performClick()

        composeRule.onNodeWithText("What these buttons do").assertIsDisplayed()
        composeRule.onNodeWithText("Show the QR code for others to join this activity")
            .assertIsDisplayed()
    }

    @Test
    fun stoppingAnActivityReturnsToIdleWithAConfirmationMessage() {
        composeRule.onNodeWithTag("sign_in_fab").performClick()
        composeRule.onNodeWithTag("start_activity_fab").performClick()

        composeRule.onNodeWithTag("stop_activity_fab").performClick()

        composeRule.onNodeWithTag("join_activity_fab").assertIsDisplayed()
        composeRule.onNodeWithText("You are no longer participating in an activity")
            .assertIsDisplayed()
    }
}
