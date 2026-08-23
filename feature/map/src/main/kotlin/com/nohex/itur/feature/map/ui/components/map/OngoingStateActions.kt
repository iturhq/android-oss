/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.feature.map.ui.components.map

internal data class OngoingStateActions(
    val onStopRequested: () -> Unit,
    val onQrRequested: () -> Unit,
    val onTrackUserRequested: () -> Unit,
    val onTrackGroupRequested: () -> Unit,
    val onAttentionRequest: () -> Unit,
    val onHelpRequested: () -> Unit,
)
