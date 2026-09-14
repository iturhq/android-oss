/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:Suppress("MatchingDeclarationName")

package cat.itur.app.core.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cat.itur.app.core.ui.GeneratedPreview
import cat.itur.app.core.ui.R
import cat.itur.app.core.ui.theme.IturTheme

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
            AnchoredSweepProgressIndicator()
            label?.let {
                Text(text = label)
            }
            Icon(
                painter = painterResource(R.drawable.core_ui_itur_overlay),
                contentDescription = stringResource(R.string.core_ui_splash_icon),
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

@Composable
private fun AnchoredSweepProgressIndicator() {
    val transition = rememberInfiniteTransition(label = "loading arc")
    val sweepProgress by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "loading arc sweep",
        )
    val geometry = loadingArcGeometry(sweepProgress)
    val color = MaterialTheme.colorScheme.primary
    val strokeWidth = 12.dp

    Canvas(
        modifier =
        Modifier
            .size(64.dp)
            .progressSemantics(),
    ) {
        drawArc(
            color = color,
            startAngle = geometry.startAngle,
            sweepAngle = geometry.sweepAngle,
            useCenter = false,
            style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round),
        )
    }
}

@GeneratedPreview
@Preview(showBackground = true)
@Composable
fun IturProgressIndicatorPreview() {
    IturTheme {
        IturProgressIndicator()
    }
}

@GeneratedPreview
@Preview(showBackground = true)
@Composable
fun IturProgressIndicatorWithTextPreview() {
    IturTheme {
        IturProgressIndicator(label = "Loading...")
    }
}
