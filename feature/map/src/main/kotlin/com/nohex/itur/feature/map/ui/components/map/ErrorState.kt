/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.feature.map.ui.components.map

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import com.nohex.itur.core.ui.R

@Composable
fun ErrorState(
    modifier: Modifier = Modifier,
    message: String? = null,
) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxSize(),
    ) {
        Image(
            painter = painterResource(R.drawable.core_ui_itur_overlay),
            contentDescription = "Map placeholder",
            modifier = Modifier
                .alpha(.3f)
                .fillMaxSize(.6f),
        )
        Text(text = "The map cannot be shown", style = MaterialTheme.typography.titleLarge)
        Text(
            text = "Please contact the manufacturer",
            style = MaterialTheme.typography.labelLarge,
        )
        message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun MapErrorPreview() {
    ErrorState(message = "Technical difficulties")
}
