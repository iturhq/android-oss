/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map.ui.components.map

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cat.itur.app.core.model.ParticipantSignal

private object StatusSurfaceColors {
    const val DELAYED = 0xFFFFE0B2
    const val ON_DELAYED = 0xFF4E2600
}

@Composable
internal fun BoxScope.SafetyStatusSurface(presentation: OngoingState) {
    val status = safetyStatusPresentation(presentation) ?: return
    Surface(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .padding(start = 72.dp, end = 72.dp, top = 16.dp)
            .testTag(status.tag)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        color = status.containerColor,
        contentColor = status.contentColor,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 6.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(status.headline, style = MaterialTheme.typography.titleSmall)
            status.details.forEach { detail ->
                Text(detail, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun safetyStatusPresentation(presentation: OngoingState): SafetyStatusPresentation? =
    if (presentation.isOrganizer) {
        organizerSafetyStatus(presentation.participantSignals)
    } else {
        participantSafetyStatus(presentation.selfSignal)
    }

@Composable
private fun participantSafetyStatus(signal: ParticipantSignal?): SafetyStatusPresentation? = when (signal) {
    ParticipantSignal.DELAYED -> SafetyStatusPresentation(
        tag = "participant_signal_confirmation",
        headline = "Delayed status shared",
        details = listOf("The organiser and group know you will catch up."),
        containerColor = Color(StatusSurfaceColors.DELAYED),
        contentColor = Color(StatusSurfaceColors.ON_DELAYED),
    )
    ParticipantSignal.NEEDS_HELP -> SafetyStatusPresentation(
        tag = "participant_signal_confirmation",
        headline = "Help request shared",
        details = listOf("The organiser and group can see your red alert."),
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    )
    null -> null
}

@Composable
private fun organizerSafetyStatus(signals: List<ParticipantSignalStatus>): SafetyStatusPresentation? {
    if (signals.isEmpty()) return null
    val needsHelp = signals
        .filter { it.signal == ParticipantSignal.NEEDS_HELP }
        .map { it.participantName }
    val delayed = signals
        .filter { it.signal == ParticipantSignal.DELAYED }
        .map { it.participantName }
    val details = buildList {
        if (needsHelp.isNotEmpty()) add("Needs help: ${needsHelp.joinToString()}")
        if (delayed.isNotEmpty()) add("Delayed: ${delayed.joinToString()}")
    }
    return SafetyStatusPresentation(
        tag = "organizer_signal_alert",
        headline = if (needsHelp.isNotEmpty()) "Participant needs help" else "Participant delayed",
        details = details,
        containerColor = if (needsHelp.isNotEmpty()) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            Color(StatusSurfaceColors.DELAYED)
        },
        contentColor = if (needsHelp.isNotEmpty()) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            Color(StatusSurfaceColors.ON_DELAYED)
        },
    )
}

private data class SafetyStatusPresentation(
    val tag: String,
    val headline: String,
    val details: List<String>,
    val containerColor: Color,
    val contentColor: Color,
)
