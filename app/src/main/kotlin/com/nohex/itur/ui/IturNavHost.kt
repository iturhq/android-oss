/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.nohex.itur.feature.map.ui.MapScreen

/**
 * Top-level navigation routes. `feature:map` is the only feature module today; new destinations
 * get their own route constant here as they're added, rather than inlining route strings at
 * each call site.
 */
object IturDestinations {
    const val MAP_ROUTE = "map"
}

/**
 * The app's real navigation graph, replacing the direct `MapScreen()` call `IturApp` used before
 * any route beyond it existed. A single destination today; adding a second is a matter of
 * registering another `composable(...)` block and a route constant above, not restructuring
 * this host.
 */
@Composable
fun IturNavHost(
    appState: IturAppState,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = appState.navController,
        startDestination = IturDestinations.MAP_ROUTE,
        modifier = modifier,
    ) {
        composable(IturDestinations.MAP_ROUTE) {
            MapScreen()
        }
    }
}
