/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import cat.itur.app.feature.map.ui.MapScreen
import kotlinx.serialization.Serializable

@Serializable
data object MapRoute

fun NavGraphBuilder.mapScreen() {
    composable<MapRoute> {
        MapScreen()
    }
}
