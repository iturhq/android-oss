/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.feature.map

import android.Manifest
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.window.Dialog
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.nohex.itur.core.data.TestFixtures
import com.nohex.itur.core.data.repository.SignInFailureReason
import com.nohex.itur.core.data.repository.SignInResult
import com.nohex.itur.core.domain.id.IturActivityId
import com.nohex.itur.core.domain.id.url
import com.nohex.itur.core.model.IturActivityStatus
import com.nohex.itur.core.ui.theme.IturTheme
import com.nohex.itur.feature.map.ui.MapScreen
import com.nohex.itur.feature.map.ui.QrCustomization
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer
import javax.inject.Inject

/**
 * Executable scenarios for UC-03 through UC-24. Permission-dialog scenarios UC-01/02 live in
 * [PermissionUseCasesTest], where UIAutomator can drive the OS-owned dialog.
 */
@HiltAndroidTest
class CanonicalUseCasesTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.CAMERA,
    )

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    @Inject
    lateinit var users: ScenarioUserRepository

    @Inject
    lateinit var activities: ScenarioActivityRepository

    @Inject
    lateinit var locations: ScenarioLocationRepository

    @Inject
    lateinit var locationClient: FakeLocationClient

    @Before
    fun setUp() {
        hiltRule.inject()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            MapLibre.getInstance(
                ApplicationProvider.getApplicationContext<Context>(),
                "",
                WellKnownTileServer.MapLibre,
            )
        }
    }

    private fun launch(
        scanCode: String? = null,
        readyTag: String = "join_activity_fab",
    ) {
        composeRule.setContent {
            IturTheme {
                MapScreen(
                    locationPermissionCheck = { true },
                    qrCustomization = QrCustomization(
                        scanSheet = { _, onScanSuccess ->
                            Dialog(onDismissRequest = {}) {
                                Column {
                                    Text("Scan an activity QR to join")
                                    scanCode?.let { code ->
                                        Button(
                                            onClick = { onScanSuccess(code) },
                                            modifier = Modifier.testTag("emit_qr_scan"),
                                        ) {
                                            Text("Emit test scan")
                                        }
                                    }
                                }
                            }
                        },
                    ),
                )
            }
        }
        waitForTag(readyTag)
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun signIn() {
        composeRule.onNodeWithTag("sign_in_fab").performClick()
        waitForTag("sign_out_fab")
    }

    private fun startAsOrganizer() {
        // The default fixture seeds an ONGOING activity already owned by this same organizer
        // (needed by uc20's auto-resume scenario, which doesn't go through this helper) --
        // clear it first so MEMB-4B18's single-active-activity rule doesn't block this
        // "start a brand new activity" flow with a activity the test itself never asked for.
        activities.replaceActivities(emptyList())
        signIn()
        composeRule.onNodeWithTag("start_activity_fab").performClick()
        waitForTag("show_qr_fab")
    }

    private fun joinAsParticipant() {
        composeRule.onNodeWithTag("join_activity_fab").performClick()
        waitForTag("emit_qr_scan")
        composeRule.onNodeWithTag("emit_qr_scan").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            activities.addParticipantCount.get() == 1
        }
        waitForTag("hail_organiser_fab")
    }

    @Test
    fun uc03_signInHappyPath() {
        launch()
        signIn()

        composeRule.onNodeWithTag("join_activity_fab").assertIsDisplayed()
        composeRule.onNodeWithTag("start_activity_fab").assertIsDisplayed()
        assertEquals(users.registered, users.current)
    }

    @Test
    fun uc04_signInFailureStaysAnonymousAndShowsStableRetry() {
        users.signInResult = SignInResult.Failure(SignInFailureReason.SERVICE_UNAVAILABLE)
        launch()

        composeRule.onNodeWithTag("sign_in_fab").performClick()

        composeRule.onNodeWithText(
            "Sign-in is temporarily unavailable. Check your connection and try again.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Try again").assertIsDisplayed()
        composeRule.onNodeWithTag("sign_in_fab").assertIsDisplayed()
        composeRule.onNodeWithTag("start_activity_fab").assertDoesNotExist()
        listOf(
            "Requests from this Android client application com.nohex.itur.pro are blocked",
            "FirebaseAuthException",
            "api_key=provider-secret",
            "token=provider-token",
        ).forEach { rawDetail -> composeRule.onNodeWithText(rawDetail).assertDoesNotExist() }

        users.signInResult = null
        composeRule.onNodeWithText("Try again").performClick()
        waitForTag("sign_out_fab")
        assertEquals(users.registered, users.current)
    }

    @Test
    fun uc05_signOutReturnsToAnonymousIdleState() {
        launch()
        signIn()

        composeRule.onNodeWithTag("sign_out_fab").performClick()

        waitForTag("sign_in_fab")
        composeRule.onNodeWithTag("start_activity_fab").assertDoesNotExist()
        assertEquals(users.anonymous, users.current)
    }

    @Test
    fun uc06_startActivityHappyPath() {
        launch()
        startAsOrganizer()

        composeRule.onNodeWithTag("recenter_fab").assertIsDisplayed()
        composeRule.onNodeWithTag("zoom_group_fab").assertIsDisplayed()
        composeRule.onNodeWithTag("stop_activity_fab").assertIsDisplayed()
        composeRule.onNodeWithTag("hail_organiser_fab").assertDoesNotExist()
    }

    @Test
    fun uc07_anonymousUserMustSignInBeforeStartIsOffered() {
        launch()

        composeRule.onNodeWithTag("sign_in_fab").assertIsDisplayed()
        composeRule.onNodeWithTag("start_activity_fab").assertDoesNotExist()
        composeRule.onNodeWithTag("show_qr_fab").assertDoesNotExist()
    }

    @Test
    fun uc08_activityCreationFailureReturnsToIdleWithError() {
        // See startAsOrganizer()'s comment: the default fixture seeds an ONGOING activity
        // already owned by this organizer, which would otherwise trip MEMB-4B18's
        // single-active-activity rule before this test's own induced failure ever runs.
        activities.replaceActivities(emptyList())
        launch()
        signIn()
        activities.createFailure = "Activity creation failed"

        composeRule.onNodeWithTag("start_activity_fab").performClick()

        composeRule.onNodeWithText("Activity creation failed").assertIsDisplayed()
        composeRule.onNodeWithTag("start_activity_fab").assertIsDisplayed()
        composeRule.onNodeWithTag("show_qr_fab").assertDoesNotExist()
    }

    @Test
    fun uc09_joinActivityFromValidQr() {
        launch(TestFixtures.ONGOING_ACTIVITY_ID.url)
        joinAsParticipant()

        composeRule.onNodeWithContentDescription("Exit activity").assertIsDisplayed()
        composeRule.onNodeWithTag("show_qr_fab").assertDoesNotExist()
    }

    @Test
    fun uc11_invalidQrIsIgnoredAndScannerStaysOpen() {
        launch("https://example.invalid/not-an-itur-activity")

        composeRule.onNodeWithTag("join_activity_fab").performClick()
        composeRule.onNodeWithTag("emit_qr_scan").performClick()

        composeRule.onNodeWithText("Scan an activity QR to join").assertIsDisplayed()
        composeRule.onNodeWithTag("stop_activity_fab").assertDoesNotExist()
    }

    @Test
    fun uc12_missingActivityReturnsToIdleWithSpecificError() {
        val missing = IturActivityId("ZZZZZZZZZZZZZZZZZZZZ")
        launch(missing.url)

        composeRule.onNodeWithTag("join_activity_fab").performClick()
        composeRule.onNodeWithTag("emit_qr_scan").performClick()

        composeRule.onNodeWithText("Activity ${missing.value} not found").assertIsDisplayed()
        composeRule.onNodeWithTag("hail_organiser_fab").assertDoesNotExist()
    }

    @Test
    fun uc13_organizerDisplaysGeneratedQr() {
        launch()
        startAsOrganizer()

        composeRule.onNodeWithTag("show_qr_fab").performClick()

        composeRule.onNodeWithText("Scan this QR to join the activity").assertIsDisplayed()
        waitForTag("activity_qr_image")
        composeRule.onNodeWithTag("activity_qr_image").assertIsDisplayed()
    }

    @Test
    fun uc14_organizerStopsActivityForEveryone() {
        launch()
        startAsOrganizer()
        val activityId = activities.activity(IturActivityId("createdActivity00001"))?.id

        composeRule.onNodeWithTag("stop_activity_fab").performClick()

        waitForTag("join_activity_fab")
        composeRule.onNodeWithText("You are no longer participating in an activity")
            .assertIsDisplayed()
        assertEquals(IturActivityStatus.FINISHED, activityId?.let(activities::activity)?.status)
        assertEquals(1, locations.removeCount.get())
    }

    @Test
    fun uc15_participantExitsWithoutFinishingActivity() {
        launch(TestFixtures.ONGOING_ACTIVITY_ID.url)
        joinAsParticipant()

        composeRule.onNodeWithTag("stop_activity_fab").performClick()

        waitForTag("join_activity_fab")
        assertEquals(
            IturActivityStatus.ONGOING,
            activities.activity(TestFixtures.ONGOING_ACTIVITY_ID)?.status,
        )
    }

    @Test
    fun uc16_leaveFailureIsVisibleAndDoesNotCrash() {
        launch(TestFixtures.ONGOING_ACTIVITY_ID.url)
        joinAsParticipant()
        locations.removeFailure = IllegalStateException("test removal failed")

        composeRule.onNodeWithTag("stop_activity_fab").performClick()

        composeRule.onNodeWithText(
            "Failed to leave activity ${TestFixtures.ONGOING_ACTIVITY_ID}",
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("join_activity_fab").assertIsDisplayed()
    }

    @Test
    fun uc17_recenterAfterLocationFixKeepsOngoingState() {
        launch()
        startAsOrganizer()
        locationClient.emit(51.5, -0.1)

        composeRule.onNodeWithTag("recenter_fab").performClick()

        composeRule.onNodeWithTag("stop_activity_fab").assertIsDisplayed()
    }

    @Test
    fun uc18_trackGroupWithKnownLocationsKeepsOngoingState() {
        launch(TestFixtures.ONGOING_ACTIVITY_ID.url)
        joinAsParticipant()
        locationClient.emit(51.5, -0.1)

        composeRule.onNodeWithTag("zoom_group_fab").performClick()

        composeRule.onNodeWithTag("hail_organiser_fab").assertIsDisplayed()
    }

    @Test
    fun uc19_participantHailsOrganizerExactlyOnce() {
        launch(TestFixtures.ONGOING_ACTIVITY_ID.url)
        joinAsParticipant()

        composeRule.onNodeWithTag("hail_organiser_fab").performClick()
        composeRule.waitUntil { activities.attentionRequestCount.get() == 1 }

        assertEquals(1, activities.attentionRequestCount.get())
        assertTrue(
            users.anonymous.id in
                activities.activity(TestFixtures.ONGOING_ACTIVITY_ID)!!.attentionRequests,
        )
        composeRule.onNodeWithTag("hail_organiser_fab").assertIsDisplayed()
    }

    @Test
    fun uc20_organizerActivityAutoResumesOnColdStart() {
        users.current = users.registered
        launch(readyTag = "show_qr_fab")

        composeRule.onNodeWithTag("stop_activity_fab").assertIsDisplayed()
        composeRule.onNodeWithTag("hail_organiser_fab").assertDoesNotExist()
    }

    @Test
    fun uc20_participantActivityAutoResumesOnColdStart() {
        users.current = users.participant
        launch(readyTag = "hail_organiser_fab")

        composeRule.onNodeWithContentDescription("Exit activity").assertIsDisplayed()
        composeRule.onNodeWithTag("show_qr_fab").assertDoesNotExist()
    }

    @Test
    fun uc21_zeroParticipantActivityCanTrackGroup() {
        launch()
        startAsOrganizer()

        composeRule.onNodeWithTag("zoom_group_fab").performClick()

        composeRule.onNodeWithTag("show_qr_fab").assertIsDisplayed()
        composeRule.onNodeWithTag("stop_activity_fab").assertIsDisplayed()
    }

    @Test
    fun uc22_pendingHailsDoNotExposeParticipantActionToOrganizer() {
        users.current = users.registered
        activities.replaceActivities(
            listOf(
                TestFixtures.ongoingActivity.copy(
                    attentionRequests = listOf(TestFixtures.PARTICIPANT_1_ID),
                ),
            ),
        )
        launch(readyTag = "show_qr_fab")

        composeRule.onNodeWithTag("hail_organiser_fab").assertDoesNotExist()
        assertEquals(
            listOf(TestFixtures.PARTICIPANT_1_ID),
            activities.activity(TestFixtures.ONGOING_ACTIVITY_ID)?.attentionRequests,
        )
    }

    @Test
    fun uc23_autoResumeFailureCanRetry() {
        users.current = users.registered
        activities.getActivityFailuresRemaining = 1
        launch()

        composeRule.onNodeWithText("The ongoing activity could not be resumed.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Try again").performClick()

        waitForTag("show_qr_fab")
    }

    @Test
    fun uc24_finishedActivityReturnsSilentlyToIdle() {
        users.current = users.registered
        activities.replaceActivities(
            listOf(
                TestFixtures.ongoingActivity.copy(status = IturActivityStatus.FINISHED),
            ),
        )
        launch()

        composeRule.onNodeWithTag("join_activity_fab").assertIsDisplayed()
        composeRule.onNodeWithTag("show_qr_fab").assertDoesNotExist()
        composeRule.onNodeWithText("The ongoing activity could not be resumed.")
            .assertDoesNotExist()
    }
}
