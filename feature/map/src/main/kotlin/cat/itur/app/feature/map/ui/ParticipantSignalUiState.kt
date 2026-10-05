/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map.ui

import cat.itur.app.core.model.ParticipantSignal

data class ParticipantSignalUiState(
    val confirmed: ParticipantSignal? = null,
    val inFlight: ParticipantSignalAction? = null,
)

enum class ParticipantSignalAction {
    REPORT_DELAYED,
    REPORT_NEEDS_HELP,
    CLEAR,
}

internal fun ParticipantSignal?.asAction(): ParticipantSignalAction = when (this) {
    ParticipantSignal.DELAYED -> ParticipantSignalAction.REPORT_DELAYED
    ParticipantSignal.NEEDS_HELP -> ParticipantSignalAction.REPORT_NEEDS_HELP
    null -> ParticipantSignalAction.CLEAR
}
