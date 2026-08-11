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
import androidx.test.rule.GrantPermissionRule
import com.nohex.itur.core.data.TestFixtures
import com.nohex.itur.core.data.repository.ActivityRepository
import com.nohex.itur.core.ui.theme.IturTheme
import com.nohex.itur.feature.map.ui.MapScreen
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer
import javax.inject.Inject

/**
 * Instrumented tests for [MapScreen], exercising it through the demo flavor's real Hilt graph
 * with [FakeLocationClient] (via [TestLocationModule]) standing in for GPS hardware, and the
 * demo flavor's own `core:data-fake` repositories standing in for Firebase. Tracked as
 * `AOSS-45FE`.
 */
@HiltAndroidTest
class MapScreenTest {

    @Inject
    lateinit var locationClient: FakeLocationClient

    @Inject
    lateinit var activityRepository: ActivityRepository

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    // Grant upfront so the system permission dialogs MapScreen requests on launch don't cover
    // the UI and block Compose test interactions. POST_NOTIFICATIONS is deliberately not
    // requested here: it doesn't exist as a runtime permission before API 33, and MapScreen
    // only asks for it on Build.VERSION.SDK_INT >= TIRAMISU in the first place.
    @get:Rule(order = 1)
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.CAMERA,
    )

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
        // Instrumented library tests run under HiltTestApplication rather than IturApplication,
        // so repeat the production process-level MapLibre initialization before creating MapView.
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            MapLibre.getInstance(
                ApplicationProvider.getApplicationContext<Context>(),
                "",
                WellKnownTileServer.MapLibre,
            )
        }
        composeRule.setContent {
            IturTheme {
                MapScreen(locationPermissionCheck = { true })
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
    fun idleMapRequestsADeviceLocationForInitialCentering() {
        composeRule.waitUntil(timeoutMillis = 10_000) { locationClient.hasActiveRequest }
    }

    @Test
    fun signingInRevealsStartActivity() {
        composeRule.onNodeWithTag("sign_in_fab").performClick()

        composeRule.onNodeWithTag("sign_out_fab").assertIsDisplayed()
        composeRule.onNodeWithTag("start_activity_fab").assertIsDisplayed()
    }

    /**
     * The demo flavor's [com.nohex.itur.core.data.di.FakeDataModule] seeds an ONGOING activity
     * already owned by the same organizer identity `sign_in_fab` signs in as (so the demo app
     * has something to show out of the box). MEMB-4B18's single-active-activity rule now blocks
     * that same organizer from starting a *second* one -- clear the seed first so these
     * "start a brand new activity" scenarios exercise a genuinely fresh state.
     */
    private fun startAsOrganizer() {
        runBlocking {
            activityRepository.deleteActivity(TestFixtures.ONGOING_ACTIVITY_ID)
            activityRepository.deleteActivity(TestFixtures.DRAFT_ACTIVITY_ID)
        }
        composeRule.onNodeWithTag("sign_in_fab").performClick()
        composeRule.onNodeWithTag("start_activity_fab").performClick()
    }

    @Test
    fun startingAnActivityShowsOngoingControlsAsOrganizer() {
        startAsOrganizer()

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
        startAsOrganizer()

        composeRule.onNodeWithTag("help_fab").performClick()

        composeRule.onNodeWithText("What these buttons do").assertIsDisplayed()
        composeRule.onNodeWithText("Show the QR code for others to join this activity")
            .assertIsDisplayed()
    }

    @Test
    fun stoppingAnActivityReturnsToIdleWithAConfirmationMessage() {
        startAsOrganizer()

        composeRule.onNodeWithTag("stop_activity_fab").performClick()

        composeRule.onNodeWithTag("join_activity_fab").assertIsDisplayed()
        composeRule.onNodeWithText("You are no longer participating in an activity")
            .assertIsDisplayed()
    }
}
