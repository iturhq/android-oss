/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
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
import cat.itur.app.core.ui.theme.IturTheme
import cat.itur.app.ui.ClearGpxTrackAction
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class GpxClearActionTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var repository: PersistedGpxTrackRepository

    @Before
    fun clearStoredTrack() {
        repository = PersistedGpxTrackRepository(context)
        runBlocking { repository.setTrack(null) }
        composeRule.setContent {
            IturTheme {
                Box { ClearGpxTrackAction(repository) }
            }
        }
    }

    @After
    fun cleanUpStoredTrack() = setTrack(null)

    @Test
    fun clearActionTracksStoredGpxAndConfirmation() {
        composeRule.onAllNodesWithTag("clear_gpx_track_button").assertCountEquals(0)
        setTrack(track())

        composeRule.onNodeWithTag("clear_gpx_track_button").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Clear GPX track?").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.onNodeWithTag("clear_gpx_track_button").assertIsDisplayed()
        assertNotNull(PersistedGpxTrackRepository(context).track.value)

        composeRule.onNodeWithTag("clear_gpx_track_button").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Clear track").performClick()

        composeRule.waitUntil(5_000) {
            PersistedGpxTrackRepository(context).track.value == null
        }
        composeRule.onAllNodesWithTag("clear_gpx_track_button").assertCountEquals(0)
        assertNull(PersistedGpxTrackRepository(context).track.value)
    }

    private fun setTrack(track: GpxTrack?) {
        runBlocking { repository.setTrack(track) }
        composeRule.waitForIdle()
    }

    private fun track() = GpxTrack(
        lines = listOf(GpxLine(points = listOf(GpxPoint(latitude = 41.38, longitude = 2.17)))),
    )
}
