/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController

data class IturAppState(
    val navController: NavHostController,
)

@Composable
fun rememberIturAppState(
    navController: NavHostController = rememberNavController(),
): IturAppState = remember(navController) {
    IturAppState(navController)
}
