/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map

import android.Manifest
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import cat.itur.app.core.ui.theme.IturTheme
import cat.itur.app.feature.map.ui.MapScreen
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer
import javax.inject.Inject

@HiltAndroidTest
class BackendUnavailableMapSurfaceTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    @Inject
    lateinit var healthCheck: ScenarioBackendHealthCheck

    @Before
    fun setUp() {
        hiltRule.inject()
        healthCheck.failure = IllegalStateException("scenario backend unavailable")
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
    fun backendUnavailableKeepsThePersistentMapAndSafeControls() {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("join_activity_fab")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("persistent_map_surface").assertIsDisplayed()
        composeRule.onNodeWithTag("join_activity_fab").assertIsDisplayed()
        composeRule.onNodeWithTag("sign_in_fab").assertIsDisplayed()
    }
}
