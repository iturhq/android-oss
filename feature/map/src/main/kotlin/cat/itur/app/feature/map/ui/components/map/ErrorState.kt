/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map.ui.components.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import cat.itur.app.feature.map.R

@Composable
fun ErrorState(
    modifier: Modifier = Modifier,
    guidance: String? = null,
    message: String? = null,
) {
    BlockingState(
        title = stringResource(R.string.feature_map_error_title),
        message = listOfNotNull(
            guidance ?: stringResource(R.string.feature_map_error_guidance),
            message,
        ).joinToString("\n\n"),
        modifier = modifier,
    )
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun MapErrorPreview() {
    ErrorState(message = "Technical difficulties")
}
