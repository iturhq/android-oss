/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import cat.itur.app.core.domain.id.IturActivityId
import cat.itur.app.feature.map.R
import cat.itur.app.feature.map.ui.MapUiState.Ongoing
import cat.itur.app.feature.map.ui.components.help.HelpOverlay
import cat.itur.app.feature.map.ui.components.help.LocalHelpAnchorRegistry
import cat.itur.app.feature.map.ui.components.qrdisplay.QRDisplaySheet

@Composable
internal fun MapTransientSurfaces(
    viewModel: MapViewModel,
    environment: MapEnvironment,
    presentation: MapPresentation,
    interaction: MapInteractionState,
    qrCustomization: QrCustomization,
) {
    RecoverableErrorSurface(presentation.uiState)
    SignInFailureSurface(presentation.signInPresentation)
    QrDisplaySurface(presentation.ongoingActivityId, interaction, qrCustomization.displayUrl)
    QrScanSurface(viewModel, environment.context, interaction, qrCustomization.scanSheet)
    HelpSurface(interaction)
    StopActivityConfirmationSurface(viewModel, presentation, interaction)
}

@Composable
private fun StopActivityConfirmationSurface(
    viewModel: MapViewModel,
    presentation: MapPresentation,
    interaction: MapInteractionState,
) {
    val ongoing = presentation.uiState as? Ongoing
    val isOrganizer = ongoing?.organizer?.id == presentation.currentUser?.id
    if (!isOrganizer) {
        LaunchedEffect(interaction.showStopActivityConfirmation) {
            interaction.cancelStopActivityConfirmation()
        }
        return
    }
    if (interaction.showStopActivityConfirmation) {
        ConfirmationDialog(
            title = stringResource(R.string.feature_map_stop_activity_title),
            message = stringResource(R.string.feature_map_stop_activity_message),
            confirmLabel = stringResource(R.string.feature_map_stop_activity),
            onCancel = interaction::cancelStopActivityConfirmation,
            onConfirm = {
                interaction.confirmStopActivity {
                    interaction.isDirectionOfTravel = false
                    interaction.stopCameraTracking()
                    viewModel.leaveActivity()
                }
            },
        )
    }
}

@Composable
private fun SignInFailureSurface(failure: SignInFailurePresentation?) {
    failure ?: return
    if (failure.retryable) {
        RecoverableErrorDialog(
            message = failure.message,
            onRetry = failure.onRetry,
            onCancel = failure.onDismiss,
        )
    } else {
        ModalAlert(text = failure.message, onDismissRequest = failure.onDismiss)
    }
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
    qrDisplayUrl: (IturActivityId) -> String,
) {
    if (interaction.showQrDisplaySheet) {
        ongoingActivityId?.let {
            QRDisplaySheet(
                activityId = it,
                onDismissRequest = { interaction.showQrDisplaySheet = false },
                qrUrl = qrDisplayUrl,
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
