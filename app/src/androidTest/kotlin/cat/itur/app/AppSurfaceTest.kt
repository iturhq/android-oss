/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.rule.GrantPermissionRule
import cat.itur.app.di.AppConfigModule
import cat.itur.app.observability.NoOpObservabilityInitializer
import cat.itur.app.ui.IturDestinations
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AppSurfaceTest {
    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.CAMERA,
    )

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun applicationStartsAtTheMapRouteAndShowsItsLicenseAction() {
        assertEquals("map", IturDestinations.MAP_ROUTE)
        composeRule.onNodeWithTag("oss_licenses_button").assertIsDisplayed()
    }

    @Test
    fun appOwnedConfigurationAndLocalObservabilityAreAvailable() {
        assertEquals(2_000L, AppConfigModule.provideLocationUpdateConfig().updateIntervalMillis)
        NoOpObservabilityInitializer().initialize()
    }
}
