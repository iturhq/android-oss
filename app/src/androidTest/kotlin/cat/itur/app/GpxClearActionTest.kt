/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import cat.itur.app.core.data.repository.PersistedGpxTrackRepository
import cat.itur.app.core.model.GpxLine
import cat.itur.app.core.model.GpxPoint
import cat.itur.app.core.model.GpxTrack
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class GpxClearActionTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun clearStoredTrack() = setTrack(null)

    @After
    fun cleanUpStoredTrack() = setTrack(null)

    @Test
    fun clearActionIsAbsentWithoutATrack() {
        composeRule.onAllNodesWithTag("clear_gpx_track_button").assertCountEquals(0)
    }

    @Test
    fun cancellingClearKeepsTheLoadedTrack() {
        setTrack(track())

        composeRule.onNodeWithTag("clear_gpx_track_button").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Clear GPX track?").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.onNodeWithTag("clear_gpx_track_button").assertIsDisplayed()
        assertNotNull(PersistedGpxTrackRepository(context).track.value)
    }

    @Test
    fun confirmingClearRemovesTheTrackAndPersistsAcrossRestart() {
        setTrack(track())

        composeRule.onNodeWithTag("clear_gpx_track_button").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Clear track").performClick()

        composeRule.waitUntil(5_000) {
            PersistedGpxTrackRepository(context).track.value == null
        }
        composeRule.onAllNodesWithTag("clear_gpx_track_button").assertCountEquals(0)
        assertNull(PersistedGpxTrackRepository(context).track.value)
    }

    private fun setTrack(track: GpxTrack?) {
        composeRule.runOnUiThread {
            runBlocking { composeRule.activity.gpxTrackRepository.setTrack(track) }
        }
        composeRule.waitForIdle()
    }

    private fun track() = GpxTrack(
        lines = listOf(GpxLine(points = listOf(GpxPoint(latitude = 41.38, longitude = 2.17)))),
    )
}
