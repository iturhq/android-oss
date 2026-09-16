/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map.ui.components.help

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import cat.itur.app.core.ui.GeneratedPreview
import kotlin.math.roundToInt

/**
 * Full-screen scrim that darkens the map and annotates each *currently visible* FAB with its
 * description, positioned next to that FAB's real screen location -- an anchored overlay, not a
 * fixed list. Tracked as `VPOL-8CDF`: replaces the old `ModalBottomSheet`-based `HelpSheet`, which
 * visually covered the very buttons it was describing.
 *
 * [anchors] comes from the ambient [HelpAnchorRegistry] (see [LocalHelpAnchorRegistry]): only
 * buttons that are actually composed register themselves there (via [Modifier.helpAnchor]), so
 * idle vs. ongoing -- and conditionally-shown buttons within each, like sign-in/sign-out -- show
 * only their own descriptions without this composable needing to know about map state at all.
 */
@Composable
internal fun HelpOverlay(
    anchors: Map<String, HelpAnchor>,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
    var selectedAnchor by remember { mutableStateOf<HelpAnchor?>(null) }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { overlayOrigin = it.boundsInRoot().topLeft },
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(HelpOverlayScrimColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = "Dismiss help",
                    onClick = onDismissRequest,
                )
                .testTag("help_overlay"),
        )

        Text(
            text = "Tap a label for more information · Tap elsewhere to close",
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .padding(16.dp)
                .testTag("help_overlay_hint"),
        )

        val overlayWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
        anchors.forEach { (key, anchor) ->
            HelpLabel(
                key = key,
                anchor = anchor,
                overlayOrigin = overlayOrigin,
                overlayWidthPx = overlayWidthPx,
                onClick = { selectedAnchor = anchor },
            )
        }
    }

    selectedAnchor?.let { anchor ->
        HelpDetailDialog(anchor = anchor, onDismissRequest = { selectedAnchor = null })
    }
}

@Composable
private fun HelpDetailDialog(anchor: HelpAnchor, onDismissRequest: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(anchor.description) },
        text = {
            Text(
                text = anchor.detail,
                modifier = Modifier.testTag("help_detail_text"),
            )
        },
        confirmButton = {
            TextButton(
                onClick = onDismissRequest,
                modifier = Modifier.testTag("dismiss_help_detail"),
            ) {
                Text("Close")
            }
        },
        modifier = Modifier.testTag("help_detail_dialog"),
    )
}

/**
 * One button's description, placed to the right of its anchor when the button lives in the left
 * half of the screen, and to the left of it otherwise -- so labels for the left FAB column grow
 * toward the map's centre, and labels for the right column do too, without either running off
 * screen.
 */
@Composable
private fun HelpLabel(
    key: String,
    anchor: HelpAnchor,
    overlayOrigin: Offset,
    overlayWidthPx: Float,
    onClick: () -> Unit,
) {
    val density = LocalDensity.current
    val localBounds = anchor.bounds.translate(-overlayOrigin)
    val placeToTheRight = localBounds.center.x < overlayWidthPx / 2
    val gapPx = with(density) { HelpLabelGap.toPx() }
    val maxWidthPx = with(density) { HelpLabelMaxWidth.toPx() }

    OutlinedButton(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = Modifier
            .offset {
                val x = if (placeToTheRight) {
                    localBounds.right + gapPx
                } else {
                    localBounds.left - gapPx - maxWidthPx
                }
                IntOffset(x = x.roundToInt(), y = localBounds.top.roundToInt())
            }
            .widthIn(max = HelpLabelMaxWidth)
            .testTag("help_label_$key"),
    ) {
        Text(
            text = anchor.description,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private val HelpOverlayScrimColor = Color.Black.copy(alpha = 0.72f)
private val HelpLabelGap = 12.dp
private val HelpLabelMaxWidth = 220.dp

@GeneratedPreview
@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun HelpOverlayPreview() {
    HelpOverlay(
        anchors = mapOf(
            "recenter_fab" to HelpAnchor(
                description = "Recenter the map on your own location",
                detail = "Moves the map back to your latest known position without changing " +
                    "location sharing or tracking.",
                bounds = Rect(Offset(24f, 900f), Size(56f, 56f)),
            ),
            "zoom_group_fab" to HelpAnchor(
                description = "Zoom out to fit every participant on the map",
                detail = "Adjusts the map so all currently visible participant positions fit on screen.",
                bounds = Rect(Offset(24f, 1000f), Size(56f, 56f)),
            ),
            "show_qr_fab" to HelpAnchor(
                description = "Show the QR code for others to join this activity",
                detail = "Opens this activity's QR code so nearby participants can scan it and request to join.",
                bounds = Rect(Offset(900f, 1000f), Size(56f, 56f)),
            ),
        ),
        onDismissRequest = ::previewNoOp,
    )
}

@GeneratedPreview
private fun previewNoOp() = Unit
