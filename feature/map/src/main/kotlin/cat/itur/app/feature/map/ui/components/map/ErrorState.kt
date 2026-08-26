/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map.ui.components.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun ErrorState(
    modifier: Modifier = Modifier,
    guidance: String = "Please contact the manufacturer",
    message: String? = null,
) {
    BlockingState(
        title = "The map cannot be shown",
        message = listOfNotNull(guidance, message).joinToString("\n\n"),
        modifier = modifier,
    )
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun MapErrorPreview() {
    ErrorState(message = "Technical difficulties")
}
