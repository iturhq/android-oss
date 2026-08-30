/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import cat.itur.app.feature.map.R

/** A composable with a map and controls driven by [MapViewModel]. */
@Composable
@Suppress("UNUSED_PARAMETER")
fun MapScreen(
    modifier: Modifier = Modifier,
    viewModel: MapViewModel = hiltViewModel(),
    locationPermissionCheck: (Context) -> Boolean = ::hasFineLocationPermission,
    locationPermissionRequest: (((Boolean) -> Unit) -> Unit)? = null,
    cameraPermissionRequest: (((Boolean) -> Unit) -> Unit)? = null,
    qrScanSheet: (
        @Composable (
            onDismissRequest: () -> Unit,
            onScanSuccess: (String) -> Unit,
        ) -> Unit
    )? = null,
    openGlEsSupportCheck: (Context) -> OpenGlEsSupport = ::checkOpenGlEsSupport,
    qrCustomization: QrCustomization = QrCustomization(),
) {
    MapScreenCoordinator(
        modifier = modifier,
        viewModel = viewModel,
        locationPermissionCustomization = LocationPermissionCustomization(
            check = locationPermissionCheck,
            request = locationPermissionRequest,
        ),
        openGlEsSupportCheck = openGlEsSupportCheck,
        qrCustomization = qrCustomization.copy(
            scanSheet = qrScanSheet ?: qrCustomization.scanSheet,
        ),
    )
}

internal data class LocationPermissionCustomization(
    val check: (Context) -> Boolean,
    val request: (((Boolean) -> Unit) -> Unit)?,
)

private fun hasFineLocationPermission(context: Context): Boolean = ContextCompat.checkSelfPermission(
    context,
    Manifest.permission.ACCESS_FINE_LOCATION,
) == PackageManager.PERMISSION_GRANTED

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModalAlert(text: String, onDismissRequest: () -> Unit) {
    BasicAlertDialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = Modifier.wrapContentWidth().wrapContentHeight(),
            shape = MaterialTheme.shapes.large,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = text)
                Spacer(modifier = Modifier.height(24.dp))
                TextButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(R.string.feature_map_dismiss))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecoverableErrorDialog(message: String, onRetry: () -> Unit, onCancel: () -> Unit) {
    BasicAlertDialog(onDismissRequest = onCancel) {
        Surface(
            modifier = Modifier.wrapContentWidth().wrapContentHeight(),
            shape = MaterialTheme.shapes.large,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = message)
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.align(Alignment.End)) {
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.feature_map_cancel)) }
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.feature_map_try_again)) }
                }
            }
        }
    }
}

/** A non-dismissible overlay shown while one or more required services are unavailable. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackendUnavailableDialog(
    failingServiceNames: List<String>,
    countdown: Int?,
    onRetryNow: () -> Unit,
    onExit: () -> Unit,
) {
    BasicAlertDialog(onDismissRequest = {}) {
        Surface(
            modifier = Modifier.wrapContentWidth().wrapContentHeight(),
            shape = MaterialTheme.shapes.large,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    stringResource(R.string.feature_map_service_unavailable),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    stringResource(
                        R.string.feature_map_service_connection_failed,
                        failingServiceNames.joinToString(),
                    ),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    countdown?.let {
                        stringResource(R.string.feature_map_retrying_in, it)
                    } ?: stringResource(R.string.feature_map_checking),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.align(Alignment.End)) {
                    TextButton(onClick = onExit) {
                        Text(stringResource(R.string.feature_map_exit))
                    }
                    TextButton(onClick = onRetryNow) {
                        Text(stringResource(R.string.feature_map_retry_now))
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun PreviewModalAlert() {
    ModalAlert("This is a modal alert.") {}
}
