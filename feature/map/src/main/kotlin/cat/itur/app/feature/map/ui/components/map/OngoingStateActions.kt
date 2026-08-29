/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map.ui.components.map

import cat.itur.app.core.model.ParticipantSignal

internal data class OngoingStateActions(
    val onStopRequested: () -> Unit,
    val onQrRequested: () -> Unit,
    val onTrackUserRequested: () -> Unit,
    val onTrackGroupRequested: () -> Unit,
    val onOrientationToggleRequested: () -> Unit,
    val onParticipantSignalRequested: (ParticipantSignal?) -> Unit,
    val onHelpRequested: () -> Unit,
)
