/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.feature.map.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import com.nohex.itur.core.domain.id.IturActivityId
import com.nohex.itur.feature.map.ui.components.help.HelpOverlay
import com.nohex.itur.feature.map.ui.components.help.LocalHelpAnchorRegistry
import com.nohex.itur.feature.map.ui.components.qrdisplay.QRDisplaySheet

@Composable
internal fun MapTransientSurfaces(
    viewModel: MapViewModel,
    environment: MapEnvironment,
    presentation: MapPresentation,
    interaction: MapInteractionState,
    qrScanSheet: @Composable (onDismissRequest: () -> Unit, onScanSuccess: (String) -> Unit) -> Unit,
) {
    RecoverableErrorSurface(presentation.uiState)
    QrDisplaySurface(presentation.ongoingActivityId, interaction)
    QrScanSurface(viewModel, environment.context, interaction, qrScanSheet)
    HelpSurface(interaction)
}

@Composable
private fun RecoverableErrorSurface(uiState: MapUiState) {
    (uiState as? MapUiState.RecoverableError)?.let { error ->
        RecoverableErrorDialog(
            message = error.message,
            onRetry = error.onRetry,
            onCancel = error.onCancel,
        )
    }
}

@Composable
private fun QrDisplaySurface(
    ongoingActivityId: IturActivityId?,
    interaction: MapInteractionState,
) {
    if (interaction.showQrDisplaySheet) {
        ongoingActivityId?.let {
            QRDisplaySheet(
                activityId = it,
                onDismissRequest = { interaction.showQrDisplaySheet = false },
            )
        }
    }
}

@Composable
private fun QrScanSurface(
    viewModel: MapViewModel,
    context: Context,
    interaction: MapInteractionState,
    qrScanSheet: @Composable (onDismissRequest: () -> Unit, onScanSuccess: (String) -> Unit) -> Unit,
) {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        interaction.cameraPermissionGranted = isGranted
        if (!isGranted) {
            interaction.showQrScanSheet = false
            interaction.localMessage = "Camera access is required to scan an activity QR code"
        }
    }
    if (interaction.showQrScanSheet) {
        LaunchedEffect(Unit) {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
            if (granted) {
                interaction.cameraPermissionGranted = true
            } else {
                launcher.launch(
                    Manifest.permission.CAMERA,
                )
            }
        }
        if (interaction.cameraPermissionGranted) {
            qrScanSheet(
                { interaction.showQrScanSheet = false },
                { code -> joinScannedActivity(viewModel, context, interaction, code) },
            )
        }
    }
}

private fun joinScannedActivity(
    viewModel: MapViewModel,
    context: Context,
    interaction: MapInteractionState,
    code: String,
) {
    IturActivityId.from(code)?.let {
        viewModel.joinActivity(activityId = it, context = context)
        interaction.showQrScanSheet = false
    }
}

@Composable
private fun HelpSurface(interaction: MapInteractionState) {
    if (interaction.showHelp) {
        val registry = LocalHelpAnchorRegistry.current
        HelpOverlay(
            anchors = registry?.anchors.orEmpty(),
            onDismissRequest = { interaction.showHelp = false },
        )
    }
}
