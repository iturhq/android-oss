/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Help
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Groups2
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QrCode
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Warning

/**
 * Itur icons as [androidx.compose.ui.graphics.vector.ImageVector]s.
 */
object IturIcons {
    val ZoomSelf = Icons.Rounded.Person
    val ZoomAll = Icons.Rounded.Groups2
    val Add = Icons.Rounded.Add

    val Join = Icons.Rounded.QrCode
    val Stop = Icons.Rounded.Stop
    val SignIn = Icons.AutoMirrored.Rounded.Login
    val SignOut = Icons.AutoMirrored.Rounded.Logout

    val Help = Icons.AutoMirrored.Rounded.Help

    val Warning = Icons.Rounded.Warning
}
