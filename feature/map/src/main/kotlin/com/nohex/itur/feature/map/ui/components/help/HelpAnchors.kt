/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.feature.map.ui.components.help

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * A single FAB's help text and its last-measured position (in root layout coordinates), so
 * [com.nohex.itur.feature.map.ui.components.help.HelpOverlay] can draw the description next to
 * the real button instead of in a fixed list. Registered by [Modifier.helpAnchor].
 */
internal data class HelpAnchor(val description: String, val bounds: Rect)

/**
 * Live registry of the help-described buttons currently in composition, keyed by a stable id
 * (each button's own test tag). Buttons register themselves via [Modifier.helpAnchor] for as long
 * as they stay composed and remove themselves on disposal, so the registry always reflects
 * exactly the buttons visible in the current map state: idle vs. ongoing show different button
 * sets, and conditionally-shown buttons within each (sign-in vs. sign-out, organizer's "Show QR"
 * vs. participant's "Hail organiser") are already mutually exclusive in composition.
 */
internal class HelpAnchorRegistry {
    private val anchorsState = mutableStateMapOf<String, HelpAnchor>()

    val anchors: Map<String, HelpAnchor> get() = anchorsState

    fun register(key: String, anchor: HelpAnchor) {
        anchorsState[key] = anchor
    }

    fun unregister(key: String) {
        anchorsState.remove(key)
    }
}

/** No registry provided means "no-op": [Modifier.helpAnchor] tolerates previews/tests fine. */
internal val LocalHelpAnchorRegistry = compositionLocalOf<HelpAnchorRegistry?> { null }

/**
 * Registers this FAB's on-screen position and [description] with the ambient
 * [HelpAnchorRegistry] (via [LocalHelpAnchorRegistry]) for as long as it stays composed.
 */
internal fun Modifier.helpAnchor(key: String, description: String): Modifier = composed {
    val registry = LocalHelpAnchorRegistry.current
    DisposableEffect(registry, key) {
        onDispose { registry?.unregister(key) }
    }
    onGloballyPositioned { coordinates ->
        registry?.register(key, HelpAnchor(description, coordinates.boundsInRoot()))
    }
}
