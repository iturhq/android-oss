/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.feature.map.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.nohex.itur.feature.map.ui.MapScreen
import kotlinx.serialization.Serializable

@Serializable
data object MapRoute

fun NavGraphBuilder.mapScreen() {
    composable<MapRoute> {
        MapScreen()
    }
}
