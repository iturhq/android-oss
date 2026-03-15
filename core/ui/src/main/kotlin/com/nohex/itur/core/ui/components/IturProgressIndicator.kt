/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nohex.itur.core.ui.R
import com.nohex.itur.core.ui.theme.IturTheme

@Composable
fun IturProgressIndicator(
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    Box(
        modifier =
        modifier
            // semi-transparent cloak
            .background(Color.White.copy(alpha = 0.7f))
            // disables interaction underneath
            .pointerInput(Unit) {},
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier.fillMaxSize(),
        ) {
            CircularProgressIndicator(
                strokeWidth = 12.dp,
                modifier = Modifier.height(64.dp),
            )
            label?.let {
                Text(text = label)
            }
            Icon(
                painter = painterResource(R.drawable.core_ui_itur_overlay),
                contentDescription = "Splash Icon",
                modifier =
                Modifier
                    .padding(top = 64.dp)
                    .alpha(.3f)
                    .fillMaxSize(0.4f),
                tint = Color.Unspecified,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun IturProgressIndicatorPreview() {
    IturTheme {
        IturProgressIndicator()
    }
}

@Preview(showBackground = true)
@Composable
fun IturProgressIndicatorWithTextPreview() {
    IturTheme {
        IturProgressIndicator(label = "Loading...")
    }
}
