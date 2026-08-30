/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map

import android.Manifest
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import cat.itur.app.core.data.TestFixtures
import cat.itur.app.core.data.repository.ActivityRepository
import cat.itur.app.core.ui.theme.IturTheme
import cat.itur.app.feature.map.ui.MapScreen
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
 * Instrumented tests for [MapScreen], using [FakeLocationClient] (via [TestLocationModule])
 * and scenario repositories from [TestDataModule] in place of device and backend services.
 * Tracked as `AOSS-45FE`.
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
    // the UI and block Compose test interactions. POST_NOTIFICATIONS is handled separately by a
    // version-aware rule because the API 29 CI AVD does not know that permission.
    @get:Rule(order = 1)
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.CAMERA,
    )

    @get:Rule(order = 2)
    val notificationPermissionRule = NotificationPermissionRule()

    @get:Rule(order = 3)
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
        composeRule.onNodeWithTag("map_orientation_fab").assertDoesNotExist()
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
     * The scenario repository seeds an ONGOING activity already owned by the same organizer
     * identity `sign_in_fab` signs in as. MEMB-4B18's single-active-activity rule now blocks
     * that organizer from starting a *second* one, so clear the seed first.
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
        composeRule.onNodeWithTag("map_orientation_fab").assertIsDisplayed()
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

        composeRule.onNodeWithTag("help_overlay").assertIsDisplayed()
        composeRule.onNodeWithText("Show the QR code for others to join this activity")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Switch between north-up and direction-of-travel map views")
            .assertIsDisplayed()
        // The organiser doesn't see "hail organiser", so the overlay must not describe it either
        // -- it only annotates buttons actually visible in the current state.
        composeRule.onNodeWithTag("help_label_hail_organiser_fab").assertDoesNotExist()
    }

    @Test
    fun helpButtonIsAvailableInIdleState() {
        // Before signing in or starting anything -- help must not require an ongoing activity.
        composeRule.onNodeWithTag("help_fab").assertIsDisplayed().performClick()

        composeRule.onNodeWithTag("help_overlay").assertIsDisplayed()
        composeRule.onNodeWithText("Join an activity by scanning its QR code").assertIsDisplayed()
        // Not signed in yet, so "sign in" is visible but "sign out" isn't -- the overlay must
        // describe only what's actually on screen.
        composeRule.onNodeWithText("Sign in to start or manage an activity").assertIsDisplayed()
    }

    @Test
    fun stoppingAnActivityReturnsToIdleWithAConfirmationMessage() {
        startAsOrganizer()

        composeRule.onNodeWithTag("stop_activity_fab").performClick()

        composeRule.onNodeWithTag("join_activity_fab").assertIsDisplayed()
        composeRule.onNodeWithTag("map_orientation_fab").assertDoesNotExist()
        composeRule.onNodeWithText("You are no longer participating in an activity")
            .assertIsDisplayed()
    }

    @Test
    fun orientationToggleSwitchesItsAccessibleModeLabel() {
        startAsOrganizer()

        composeRule.onNodeWithTag("map_orientation_fab").performClick()
        composeRule.onNodeWithContentDescription("Switch to north-up view").assertIsDisplayed()

        composeRule.onNodeWithTag("map_orientation_fab").performClick()
        composeRule.onNodeWithContentDescription("Switch to direction-of-travel view")
            .assertIsDisplayed()
    }
}
