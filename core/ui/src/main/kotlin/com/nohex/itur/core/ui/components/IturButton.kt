/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nohex.itur.core.ui.theme.IturTheme

@Composable
fun IturButton(
    label: String,
    modifier: Modifier = Modifier,
    type: ActionButtonType = ActionButtonType.PRIMARY,
    onClick: () -> Unit,
) {
    val (borderColor, contentColor) =
        when (type) {
            ActionButtonType.PRIMARY -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.primary
            ActionButtonType.SECONDARY -> MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.secondary
            ActionButtonType.DANGER -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.error
            ActionButtonType.WARNING -> MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.tertiary
        }

    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(5.dp, borderColor),
        colors =
        ButtonDefaults.outlinedButtonColors(
            contentColor = contentColor,
        ),
    ) {
        Text(
            label,
            fontSize = MaterialTheme.typography.labelLarge.fontSize,
            textAlign = TextAlign.Center,
        )
    }
}

enum class ActionButtonType {
    PRIMARY,
    SECONDARY,
    DANGER,
    WARNING,
}

@Preview(showBackground = true)
@Composable
fun DefaultButtonPreview() {
    IturTheme {
        IturButton(label = "Some action", onClick = {})
    }
}

@Preview
@Composable
fun SecondaryButtonPreview() {
    IturTheme {
        IturButton(type = ActionButtonType.SECONDARY, label = "Some action", onClick = {})
    }
}

@Preview
@Composable
fun WarningButtonPreview() {
    IturTheme {
        IturButton(type = ActionButtonType.WARNING, label = "Be careful", onClick = {})
    }
}

@Preview
@Composable
fun DangerButtonPreview() {
    IturTheme {
        IturButton(type = ActionButtonType.DANGER, label = "Take dangerous action", onClick = {})
    }
}
