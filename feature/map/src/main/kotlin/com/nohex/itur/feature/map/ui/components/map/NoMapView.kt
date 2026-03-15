/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.feature.map.ui.components.map

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.nohex.itur.core.ui.R

/**
 * A composable to not show a map, useful for previews.
 */
@Composable
fun NoMapView(
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(R.drawable.core_ui_itur_overlay),
        contentDescription = "Map placeholder",
        modifier = modifier
            .alpha(.3f)
            .fillMaxSize()
            .padding(64.dp),
    )
}
