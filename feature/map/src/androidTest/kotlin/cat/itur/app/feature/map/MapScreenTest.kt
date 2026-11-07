/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map

import android.Manifest
import android.content.Context
import android.os.SystemClock
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import cat.itur.app.core.data.TestFixtures
import cat.itur.app.core.data.repository.ActivityRepository
import cat.itur.app.core.ui.theme.IturTheme
import cat.itur.app.feature.map.ui.MapScreen
import cat.itur.app.feature.map.ui.cameraFitInsets
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer
import javax.inject.Inject
import kotlin.math.pow
import android.graphics.Color as AndroidColor

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
        composeRule.waitForIdle()
        assertEquals(1, locationClient.requestCount.get())
    }

    @Test
    fun visibleSessionOwnsOneRequestAndStopRemovesIt() {
        composeRule.waitUntil(timeoutMillis = 10_000) { locationClient.hasActiveRequest }
        composeRule.onNodeWithTag("help_fab").performClick()
        composeRule.onNodeWithTag("help_overlay").assertIsDisplayed()
        composeRule.waitForIdle()
        assertEquals(1, locationClient.requestCount.get())
        assertEquals(0, locationClient.removeCount.get())

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeRule.waitUntil(timeoutMillis = 10_000) { !locationClient.hasActiveRequest }
        assertEquals(locationClient.requestCount.get(), locationClient.removeCount.get())
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
    fun idleControlsOccupyTopRightAndBottomRightZonesWithoutOverlap() {
        val topRightZone = boundsOf("map_zone_tr")
        val rightZone = boundsOf("map_zone_r")
        val help = boundsOf("help_fab")
        val signIn = boundsOf("sign_in_fab")
        val join = boundsOf("join_activity_fab")
        val rootWidth = composeRule.onRoot().fetchSemanticsNode().boundsInRoot.width

        assertTrue(topRightZone.center.x > rootWidth / 2f)
        assertTrue(rightZone.center.x > rootWidth / 2f)
        assertTrue(help.bottom <= signIn.top)
        assertTrue(join.bottom <= signIn.top)
        assertContains(topRightZone, help)
        assertContains(rightZone, signIn)
        assertContains(rightZone, join)
        assertNoOverlaps(help, signIn, join)
    }

    @Test
    fun idleControlsStayInsideTheSystemBarSafeArea() {
        composeRule.waitForIdle()
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val safeInsets = requireNotNull(
            ViewCompat.getRootWindowInsets(composeRule.activity.window.decorView),
        ).getInsets(
            WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout(),
        )
        val topRightZone = boundsOf("map_zone_tr")
        val rightZone = boundsOf("map_zone_r")

        assertTrue(topRightZone.top >= root.top + safeInsets.top)
        assertTrue(topRightZone.right <= root.right - safeInsets.right)
        assertTrue(rightZone.right <= root.right - safeInsets.right)
        assertTrue(rightZone.bottom <= root.bottom - safeInsets.bottom)
    }

    @Test
    fun mapSurfaceStartsBelowTheStatusBar() {
        composeRule.waitForIdle()
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val safeInsets = requireNotNull(
            ViewCompat.getRootWindowInsets(composeRule.activity.window.decorView),
        ).getInsets(
            WindowInsetsCompat.Type.statusBars() or
                WindowInsetsCompat.Type.displayCutout(),
        )
        val map = boundsOf("map_surface")

        assertTrue(map.top >= root.top + safeInsets.top)
        assertTrue(map.bottom >= root.bottom - 1f)
    }

    @Test
    fun ongoingControlsOccupyTopRightAndSeparateBottomLanes() {
        startAsOrganizer()

        val topRightZone = boundsOf("map_zone_tr")
        val leftZone = boundsOf("map_zone_l")
        val rightZone = boundsOf("map_zone_r")
        val help = boundsOf("help_fab")
        val zoomGroup = boundsOf("zoom_group_fab")
        val recenter = boundsOf("recenter_fab")
        val showQr = boundsOf("show_qr_fab")
        val stop = boundsOf("stop_activity_fab")
        val rootWidth = composeRule.onRoot().fetchSemanticsNode().boundsInRoot.width

        assertTrue(topRightZone.center.x > rootWidth / 2f)
        assertTrue(leftZone.center.x < rootWidth / 2f)
        assertTrue(rightZone.center.x > rootWidth / 2f)
        assertContains(topRightZone, help)
        assertContains(leftZone, zoomGroup)
        assertContains(leftZone, recenter)
        assertContains(rightZone, showQr)
        assertContains(rightZone, stop)
        assertTrue(help.bottom <= showQr.top)
        assertTrue(recenter.bottom >= zoomGroup.bottom)
        assertTrue(stop.bottom >= showQr.bottom)
        assertNoOverlaps(help, zoomGroup, recenter, showQr, stop)
    }

    @Test
    fun ongoingGroupFitInsetsClearTheRenderedBottomLanes() {
        startAsOrganizer()

        val map = boundsOf("map_surface")
        val leftLane = union(boundsOf("recenter_fab"), boundsOf("zoom_group_fab"))
        val rightLane = union(boundsOf("show_qr_fab"), boundsOf("stop_activity_fab"))
        val insets = cameraFitInsets(
            mapBounds = map,
            leftControlBounds = leftLane,
            rightControlBounds = rightLane,
            horizontalFallback = 0,
            verticalPadding = 1,
        )

        assertTrue(insets.left > leftLane.right - map.left)
        assertTrue(insets.right > map.right - rightLane.left)
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
        composeRule.onNodeWithTag("help_label_show_qr_fab", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag("help_overlay").assertIsDisplayed()
        val showQrDetail =
            "Displays this activity's QR code so another person can scan it and request to join."
        composeRule.onNodeWithTag("help_detail_text", useUnmergedTree = true)
            .assertTextContains(showQrDetail)
        pressBack()
        composeRule.onNodeWithTag("help_detail_text", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("help_overlay").assertIsDisplayed()
        composeRule.onNodeWithTag("help_label_show_qr_fab").assertIsDisplayed()
        // The organiser doesn't see "hail organiser", so the overlay must not describe it either
        // -- it only annotates buttons actually visible in the current state.
        composeRule.onNodeWithTag("help_label_hail_organiser_fab").assertDoesNotExist()
    }

    @Test
    fun helpButtonIsAvailableInIdleState() {
        // Before signing in or starting anything -- help must not require an ongoing activity.
        composeRule.waitForIdle()
        SystemClock.sleep(750)
        val mapPixels = renderedPixelStats()
        composeRule.onNodeWithTag("help_fab").assertIsDisplayed().performClick()

        composeRule.onNodeWithTag("help_overlay").assertIsDisplayed()
        composeRule.waitForIdle()
        SystemClock.sleep(500)
        val overlayPixels = renderedPixelStats()
        println("VPOL-C41E map pixels=$mapPixels, overlay pixels=$overlayPixels")
        assertTrue(
            "Help overlay must turn most rendered viewport samples dark while the baseline " +
                "remains visibly non-dark; map=$mapPixels, overlay=$overlayPixels",
            mapPixels.darkFraction < 0.45 && overlayPixels.darkFraction >= 0.65,
        )
        composeRule.onNodeWithTag("help_overlay_hint", useUnmergedTree = true)
            .assertTextContains("Tap a label for more information", substring = true)
        composeRule.onNodeWithText("Join an activity by scanning its QR code").assertIsDisplayed()
        assertHelpOverlaySafeAndReadable(
            labelTags = listOf(
                "help_label_text_join_activity_fab",
                "help_label_text_sign_in_fab",
            ),
        )
        composeRule.onNodeWithTag("help_label_join_activity_fab", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag("help_overlay").assertIsDisplayed()
        val joinDetail =
            "Opens the camera to scan an organiser's activity QR code, then asks to join that activity."
        composeRule.onNodeWithTag("help_detail_text", useUnmergedTree = true)
            .assertTextContains(joinDetail)
        composeRule.onNodeWithTag("dismiss_help_detail", useUnmergedTree = true).assertIsDisplayed()
        pressBack()
        composeRule.onNodeWithTag("help_detail_text", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("help_overlay").assertIsDisplayed()
        composeRule.onNodeWithTag("help_label_join_activity_fab").assertIsDisplayed()
        // Not signed in yet, so "sign in" is visible but "sign out" isn't -- the overlay must
        // describe only what's actually on screen.
        composeRule.onNodeWithText("Sign in to start or manage an activity").assertIsDisplayed()
    }

    private fun renderedPixelStats(): RenderedPixelStats {
        val screenshot = checkNotNull(
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot(),
        )
        return try {
            var total = 0.0
            var darkSamples = 0
            var samples = 0
            for (xStep in 2..18) {
                for (yStep in 2..18) {
                    val pixel = screenshot.getPixel(
                        screenshot.width * xStep / 20,
                        screenshot.height * yStep / 20,
                    )
                    val luma = 0.2126 * AndroidColor.red(pixel) +
                        0.7152 * AndroidColor.green(pixel) +
                        0.0722 * AndroidColor.blue(pixel)
                    total += luma
                    if (luma <= 85.0) darkSamples++
                    samples++
                }
            }
            RenderedPixelStats(
                averageLuma = total / samples,
                darkFraction = darkSamples.toDouble() / samples,
            )
        } finally {
            screenshot.recycle()
        }
    }

    private data class RenderedPixelStats(
        val averageLuma: Double,
        val darkFraction: Double,
    )

    private fun assertHelpOverlaySafeAndReadable(labelTags: List<String>) {
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val safeInsets = requireNotNull(
            ViewCompat.getRootWindowInsets(composeRule.activity.window.decorView),
        ).getInsets(
            WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout(),
        )
        val hint = boundsOf("help_overlay_hint", useUnmergedTree = true)
        val chromeClearance = with(composeRule.activity.resources.displayMetrics) {
            72f * density
        }
        assertTrue(
            "Help hint $hint overlaps safe drawing bounds $safeInsets inside root $root",
            hint.top >= root.top + safeInsets.top &&
                hint.left >= root.left + safeInsets.left &&
                hint.right <= root.right - safeInsets.right,
        )
        assertTrue(
            "Help hint $hint overlaps the persistent 48dp top app chrome inside its " +
                "16dp edge lane plus 8dp gap; root=$root, safeInsets=$safeInsets",
            hint.left >= root.left + safeInsets.left + chromeClearance &&
                hint.right <= root.right - safeInsets.right - chromeClearance,
        )

        labelTags.forEach { tag ->
            val contrast = renderedContrastRatio(tag)
            println("VPOL-53A5 $tag rendered contrast=$contrast")
            assertTrue(
                "Rendered Help label $tag contrast $contrast is below 4.5:1",
                contrast >= 4.5,
            )
        }
    }

    private fun renderedContrastRatio(tag: String): Double {
        val bounds = boundsOf(tag, useUnmergedTree = true)
        val screenshot = checkNotNull(
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot(),
        )
        return try {
            val luminances = buildList {
                for (
                x in bounds.left.toInt().coerceAtLeast(0) until
                    bounds.right.toInt().coerceAtMost(screenshot.width)
                ) {
                    for (
                    y in bounds.top.toInt().coerceAtLeast(0) until
                        bounds.bottom.toInt().coerceAtMost(screenshot.height)
                    ) {
                        add(relativeLuminance(screenshot.getPixel(x, y)))
                    }
                }
            }.sorted()
            check(luminances.isNotEmpty()) { "No rendered pixels inside $tag bounds $bounds" }
            val background = luminances[luminances.size / 2]
            val foreground = luminances[(luminances.lastIndex * 99) / 100]
            val brightFraction = luminances.count { it >= 0.75 }.toDouble() / luminances.size
            assertTrue(
                "Rendered Help label $tag has no stable bright glyph pixels: $brightFraction",
                brightFraction >= 0.005,
            )
            (foreground + 0.05) / (background + 0.05)
        } finally {
            screenshot.recycle()
        }
    }

    private fun relativeLuminance(pixel: Int): Double {
        fun linear(channel: Int): Double {
            val value = channel / 255.0
            return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * linear(AndroidColor.red(pixel)) +
            0.7152 * linear(AndroidColor.green(pixel)) +
            0.0722 * linear(AndroidColor.blue(pixel))
    }

    @Test
    fun stoppingAnActivityReturnsToIdleWithAConfirmationMessage() {
        startAsOrganizer()

        composeRule.onNodeWithTag("stop_activity_fab").performClick()
        composeRule.onNodeWithText("Stop activity?").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm_stop_activity").performClick()

        composeRule.onNodeWithTag("join_activity_fab").assertIsDisplayed()
        composeRule.onNodeWithTag("map_orientation_fab").assertDoesNotExist()
        composeRule.onNodeWithText("You are no longer participating in an activity")
            .assertIsDisplayed()
    }

    @Test
    fun cancellingStopConfirmationKeepsOrganizerInActivity() {
        startAsOrganizer()

        composeRule.onNodeWithTag("stop_activity_fab").performClick()
        composeRule.onNodeWithTag("cancel_stop_activity").performClick()

        composeRule.onNodeWithText("Stop activity?").assertDoesNotExist()
        composeRule.onNodeWithTag("stop_activity_fab").assertIsDisplayed()
    }

    @Test
    fun backDismissesStopConfirmationAndKeepsOrganizerInActivity() {
        startAsOrganizer()

        composeRule.onNodeWithTag("stop_activity_fab").performClick()
        composeRule.onNodeWithText("Stop activity?").assertIsDisplayed()
        pressBack()

        composeRule.onNodeWithText("Stop activity?").assertDoesNotExist()
        composeRule.onNodeWithTag("stop_activity_fab").assertIsDisplayed()
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

    private fun boundsOf(tag: String, useUnmergedTree: Boolean = false) = composeRule.onNodeWithTag(tag, useUnmergedTree = useUnmergedTree)
        .fetchSemanticsNode().boundsInRoot

    private fun assertContains(
        container: androidx.compose.ui.geometry.Rect,
        child: androidx.compose.ui.geometry.Rect,
    ) {
        assertTrue(
            "Zone $container does not contain control $child",
            child.left >= container.left &&
                child.top >= container.top &&
                child.right <= container.right &&
                child.bottom <= container.bottom,
        )
    }

    private fun assertNoOverlaps(vararg bounds: androidx.compose.ui.geometry.Rect) {
        bounds.forEachIndexed { index, first ->
            bounds.drop(index + 1).forEach { second ->
                assertTrue("Control bounds overlap: $first and $second", !first.overlaps(second))
            }
        }
    }

    private fun union(first: Rect, second: Rect) = Rect(
        left = minOf(first.left, second.left),
        top = minOf(first.top, second.top),
        right = maxOf(first.right, second.right),
        bottom = maxOf(first.bottom, second.bottom),
    )
}
