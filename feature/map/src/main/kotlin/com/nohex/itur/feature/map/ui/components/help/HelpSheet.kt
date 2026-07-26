/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.feature.map.ui.components.help

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nohex.itur.core.ui.IturIcons
import com.nohex.itur.core.ui.theme.IturTheme

/**
 * Explains the floating action buttons shown during an ongoing activity, opened from
 * [com.nohex.itur.feature.map.ui.components.map.HelpFABs]. Tracked as `AOSS-A49A`: the button
 * existed with an empty `onClick` before this.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpSheet(
    onDismissRequest: () -> Unit,
    isOrganizer: Boolean,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            HelpSheetContent(isOrganizer)
            Spacer(modifier = Modifier.size(8.dp))
        }
    }
}

@Composable
private fun HelpSheetContent(isOrganizer: Boolean) {
    Text(
        text = "What these buttons do",
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(bottom = 16.dp),
    )
    HelpItem(IturIcons.ZoomSelf, "Recenter the map on your own location")
    HelpItem(IturIcons.ZoomAll, "Zoom out to fit every participant on the map")
    if (isOrganizer) {
        HelpItem(IturIcons.Join, "Show the QR code for others to join this activity")
    } else {
        HelpItem(IturIcons.Warning, "Hail the organiser if you need their attention")
    }
    HelpItem(IturIcons.Stop, "Stop the activity for everyone")
}

@Composable
private fun HelpItem(icon: ImageVector, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null)
        Spacer(modifier = Modifier.size(16.dp))
        Text(text = description, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * Previews [HelpSheetContent] directly rather than [HelpSheet]: [ModalBottomSheet] can't be
 * previewed standalone, since it requires a real window to anchor its sheet state to.
 */
@Preview(showBackground = true)
@Composable
private fun HelpSheetOrganizerPreview() {
    IturTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            HelpSheetContent(isOrganizer = true)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HelpSheetParticipantPreview() {
    IturTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            HelpSheetContent(isOrganizer = false)
        }
    }
}
