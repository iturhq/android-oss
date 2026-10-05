/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import cat.itur.app.core.model.ParticipantSignal
import cat.itur.app.core.ui.theme.IturTheme
import cat.itur.app.feature.map.ui.components.map.IdleState
import cat.itur.app.feature.map.ui.components.map.OngoingState
import cat.itur.app.feature.map.ui.components.map.OngoingStateActions
import cat.itur.app.feature.map.ui.components.map.UserFABs
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ServiceAwareControlsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun consumerCanMoveSignedInAuthenticationOffTheMap() {
        composeRule.setContent {
            IturTheme {
                UserFABs(
                    onSignInRequested = {},
                    onSignOutRequested = {},
                    isSignedIn = true,
                    authenticationActionsEnabled = true,
                    showSignOutOnMap = false,
                )
            }
        }

        composeRule.onNodeWithTag("sign_out_fab").assertDoesNotExist()
        composeRule.onNodeWithTag("sign_in_fab").assertDoesNotExist()
    }

    @Test
    fun disabledAuthenticationActionsKeepJoinUsable() {
        var signedIn = false
        var joined = false
        composeRule.setContent {
            IturTheme {
                IdleState(
                    onStartRequested = {},
                    onSignInRequested = { signedIn = true },
                    onSignOutRequested = {},
                    onQRRequested = { joined = true },
                    onHelpRequested = {},
                    isSignedIn = false,
                    authenticationActionsEnabled = false,
                    activityActionsEnabled = true,
                )
            }
        }

        composeRule.onNodeWithTag("sign_in_fab").assertIsNotEnabled().performClick()
        composeRule.onNodeWithTag("join_activity_fab").assertIsEnabled().performClick()

        assertFalse(signedIn)
        assertTrue(joined)
    }

    @Test
    fun disabledActivityActionsKeepJoinUsable() {
        var started = false
        var joined = false
        composeRule.setContent {
            IturTheme {
                IdleState(
                    onStartRequested = { started = true },
                    onSignInRequested = {},
                    onSignOutRequested = {},
                    onQRRequested = { joined = true },
                    onHelpRequested = {},
                    isSignedIn = true,
                    authenticationActionsEnabled = true,
                    activityActionsEnabled = false,
                )
            }
        }

        composeRule.onNodeWithTag("start_activity_fab").assertIsNotEnabled().performClick()
        composeRule.onNodeWithTag("join_activity_fab").assertIsEnabled().performClick()

        assertFalse(started)
        assertTrue(joined)
    }

    @Test
    fun disabledFirestoreActionsKeepAuthenticationUsable() {
        var signedIn = false
        var started = false
        composeRule.setContent {
            IturTheme {
                IdleState(
                    onStartRequested = { started = true },
                    onSignInRequested = { signedIn = true },
                    onSignOutRequested = {},
                    onQRRequested = {},
                    onHelpRequested = {},
                    isSignedIn = false,
                    authenticationActionsEnabled = true,
                    activityActionsEnabled = false,
                )
            }
        }

        composeRule.onNodeWithTag("sign_in_fab").assertIsEnabled().performClick()
        composeRule.onNodeWithTag("start_activity_fab").assertDoesNotExist()
        composeRule.onNodeWithTag("join_activity_fab").assertIsEnabled()

        assertTrue(signedIn)
        assertFalse(started)
    }

    @Test
    fun disabledActivityActionsKeepLocalOngoingControlsUsable() {
        var stopped = false
        var tracked = false
        composeRule.setContent {
            IturTheme {
                OngoingState(
                    actions = OngoingStateActions(
                        onStopRequested = { stopped = true },
                        onQrRequested = {},
                        onTrackUserRequested = { tracked = true },
                        onTrackGroupRequested = {},
                        onOrientationToggleRequested = {},
                        onParticipantSignalRequested = {},
                        onHelpRequested = {},
                    ),
                    presentation = OngoingState(
                        isOrganizer = false,
                        activityActionsEnabled = false,
                    ),
                )
            }
        }

        composeRule.onNodeWithTag("stop_activity_fab").assertIsNotEnabled().performClick()
        composeRule.onNodeWithTag("recenter_fab").assertIsEnabled().performClick()

        assertFalse(stopped)
        assertTrue(tracked)
    }

    @Test
    fun activeTrackingControlsExposeTheirSelectedState() {
        composeRule.setContent {
            IturTheme {
                OngoingState(
                    actions = OngoingStateActions(
                        onStopRequested = {},
                        onQrRequested = {},
                        onTrackUserRequested = {},
                        onTrackGroupRequested = {},
                        onOrientationToggleRequested = {},
                        onParticipantSignalRequested = {},
                        onHelpRequested = {},
                    ),
                    presentation = OngoingState(
                        isOrganizer = true,
                        isUserTracking = true,
                    ),
                )
            }
        }

        composeRule.onNodeWithTag("recenter_fab").assertIsSelected()
        composeRule.onNodeWithTag("zoom_group_fab").assertIsNotSelected()
    }

    @Test
    fun groupTrackingControlExposesItsSelectedState() {
        composeRule.setContent {
            IturTheme {
                OngoingState(
                    actions = OngoingStateActions(
                        onStopRequested = {},
                        onQrRequested = {},
                        onTrackUserRequested = {},
                        onTrackGroupRequested = {},
                        onOrientationToggleRequested = {},
                        onParticipantSignalRequested = {},
                        onHelpRequested = {},
                    ),
                    presentation = OngoingState(
                        isOrganizer = true,
                        isGroupTracking = true,
                    ),
                )
            }
        }

        composeRule.onNodeWithTag("recenter_fab").assertIsNotSelected()
        composeRule.onNodeWithTag("zoom_group_fab").assertIsSelected()
    }

    @Test
    fun participantWithoutSignalCanReportDelayedOrNeedsHelp() {
        setOngoingParticipantContent(OngoingState(isOrganizer = false))

        composeRule.onNodeWithTag("safety_delayed_fab").assertIsEnabled()
        composeRule.onNodeWithTag("hail_organiser_fab").assertIsEnabled()
        composeRule.onNodeWithTag("safety_ok_fab").assertDoesNotExist()
    }

    @Test
    fun delayedParticipantCanEscalateOrClearButCannotRepeatDelayed() {
        setOngoingParticipantContent(
            OngoingState(isOrganizer = false, selfSignal = ParticipantSignal.DELAYED),
        )

        val okay = composeRule.onNodeWithTag("safety_ok_fab")
            .assertIsDisplayed()
            .assertIsEnabled()
            .assertContentDescriptionEquals("I'm okay")
            .fetchSemanticsNode().boundsInRoot
        val delayed = composeRule.onNodeWithTag("safety_delayed_fab")
            .assertIsDisplayed()
            .assertIsNotEnabled()
            .assertContentDescriptionEquals("I'm stopping; go ahead")
            .fetchSemanticsNode().boundsInRoot
        val needsHelp = composeRule.onNodeWithTag("hail_organiser_fab")
            .assertIsDisplayed()
            .assertIsEnabled()
            .assertContentDescriptionEquals("I need help; converge on me")
            .fetchSemanticsNode().boundsInRoot
        val stop = composeRule.onNodeWithTag("stop_activity_fab")
            .fetchSemanticsNode().boundsInRoot
        val rightLane = composeRule.onNodeWithTag("map_zone_r")
            .fetchSemanticsNode().boundsInRoot
        val minimumTouchTarget = with(composeRule.density) { 48.dp.toPx() }

        assertTrue(okay.top < delayed.top && delayed.top < needsHelp.top && needsHelp.top < stop.top)
        listOf(okay, delayed, needsHelp).forEach { bounds ->
            assertTrue(bounds.width >= minimumTouchTarget && bounds.height >= minimumTouchTarget)
            assertTrue(bounds.left >= rightLane.left && bounds.right <= rightLane.right)
        }
    }

    @Test
    fun redParticipantCanOnlyClearSignal() {
        setOngoingParticipantContent(
            OngoingState(isOrganizer = false, selfSignal = ParticipantSignal.NEEDS_HELP),
        )

        composeRule.onNodeWithTag("safety_delayed_fab").assertIsNotEnabled()
        composeRule.onNodeWithTag("hail_organiser_fab").assertIsNotEnabled()
        composeRule.onNodeWithTag("safety_ok_fab").assertIsEnabled()
    }

    @Test
    fun inFlightSignalUpdateDisablesEverySignalAction() {
        setOngoingParticipantContent(
            OngoingState(
                isOrganizer = false,
                selfSignal = ParticipantSignal.DELAYED,
                participantSignalUpdateInFlight = true,
            ),
        )

        composeRule.onNodeWithTag("safety_delayed_fab").assertIsNotEnabled()
        composeRule.onNodeWithTag("hail_organiser_fab").assertIsNotEnabled()
        composeRule.onNodeWithTag("safety_ok_fab").assertIsNotEnabled()
    }

    @Test
    fun organizerDoesNotSeeParticipantSignalActions() {
        setOngoingParticipantContent(OngoingState(isOrganizer = true))

        composeRule.onNodeWithTag("safety_delayed_fab").assertDoesNotExist()
        composeRule.onNodeWithTag("hail_organiser_fab").assertDoesNotExist()
        composeRule.onNodeWithTag("safety_ok_fab").assertDoesNotExist()
    }

    private fun setOngoingParticipantContent(presentation: OngoingState) {
        composeRule.setContent {
            IturTheme {
                OngoingState(
                    actions = OngoingStateActions(
                        onStopRequested = {},
                        onQrRequested = {},
                        onTrackUserRequested = {},
                        onTrackGroupRequested = {},
                        onOrientationToggleRequested = {},
                        onParticipantSignalRequested = {},
                        onHelpRequested = {},
                    ),
                    presentation = presentation,
                )
            }
        }
    }
}
