/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.feature.map.ui

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.nohex.itur.core.domain.id.IturActivityId
import com.nohex.itur.core.domain.model.User
import com.nohex.itur.core.ui.components.IturProgressIndicator
import com.nohex.itur.feature.map.ui.components.map.IdleState
import com.nohex.itur.feature.map.ui.components.map.MapLibreView
import com.nohex.itur.feature.map.ui.components.map.NoMapView
import com.nohex.itur.feature.map.ui.components.map.OngoingState
import com.nohex.itur.feature.map.ui.components.qrdisplay.QRDisplaySheet
import com.nohex.itur.feature.map.ui.components.qrscan.QRScanSheet
import org.maplibre.android.maps.MapLibreMap

/**
 * A composable with a map. The buttons displayed on the map are placed by the UI state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    viewModel: MapViewModel = hiltViewModel(),
) {
    val context = LocalContext.current

    val uiState by viewModel.uiState.collectAsState()
    // The current user.
    val currentUser by viewModel.currentUser.collectAsState()
    // The identifier of the activity displayed on the map, when there is one.
    val ongoingActivityId by viewModel.ongoingActivityId.collectAsState()
    // The identifier of the organiser of the activity displayed on the map, when there is one.
    val organizerId by viewModel.organizerId.collectAsState()
    // A reference to the MapLibre map.
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    // The positions of the participants.
    val participantLocations by viewModel.participantLocations.collectAsState()
    // The last location of the device.
    val lastLocation by viewModel.lastLocation.observeAsState()
    // Whether the QR sheet is showing.
    var showQRDisplaySheet by remember { mutableStateOf(false) }
    var showQRScanSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Show a snackbar when any state carries a user-facing message.
    val displayMessage = when (val state = uiState) {
        is MapUiState.Idle -> state.message
        is MapUiState.Error -> state.message
        else -> null
    }

    // Show a dialog when an activity cannot be resumed.
    if (uiState is MapUiState.RecoverableError) {
        val error = uiState as MapUiState.RecoverableError
        RecoverableErrorDialog(
            message = error.message,
            onRetry = error.onRetry,
            onCancel = error.onCancel,
        )
    }
    LaunchedEffect(displayMessage) {
        displayMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    // Permissions.

    // Request camera permission, if needed.
    var cameraPermissionGranted by remember { mutableStateOf(false) }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        cameraPermissionGranted = isGranted
    }
    // Request location permissions, if needed.
    var locationPermissionGranted by remember { mutableStateOf(false) }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        locationPermissionGranted = isGranted
    }

    // Request location permission when entering the map screen.
    LaunchedEffect(Unit) {
        val hasLocationPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        if (hasLocationPermission) {
            locationPermissionGranted = true
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // Change the view when the ongoing activity changes.
    LaunchedEffect(ongoingActivityId) {
        ongoingActivityId?.let {
            viewModel.triggerOngoingState(it, context)
        } ?: run {
            if (uiState !is MapUiState.Idle) {
                viewModel.triggerIdleState()
            }
        }
    }

    if (showQRDisplaySheet) {
        ongoingActivityId?.let {
            QRDisplaySheet(
                activityId = it,
                onDismissRequest = { showQRDisplaySheet = false },
            )
        }
    }

    if (showQRScanSheet) {
        // Request location permission when display the QR scan sheet.
        LaunchedEffect(Unit) {
            val hasCameraPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED

            if (hasCameraPermission) {
                cameraPermissionGranted = true
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        QRScanSheet(
            onDismissRequest = { showQRScanSheet = false },
            onScanSuccess = { code ->
                IturActivityId.from(code)?.let {
                    viewModel.joinActivity(activityId = it, context = context)
                    showQRScanSheet = false
                }
            },
        )
    }

    // The scaffold provides a snackbar host; the box inside holds the map and state overlays.
    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(snackbarData = data)
            }
        },
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            // Do not show the map in previews.
            if (LocalInspectionMode.current) {
                NoMapView()
            } else {
                MapLibreView(
                    isActivityOngoing = ongoingActivityId != null,
                    organizerId = organizerId,
                    currentUserId = currentUser?.id,
                    participantLocations = participantLocations,
                    modifier = modifier.fillMaxSize(),
                    onMapReady = { map ->
                        mapLibreMap = map
                    },
                )
            }

            when (uiState) {
                is MapUiState.Loading -> IturProgressIndicator(label = "Preparing activity...")
                is MapUiState.Error,
                is MapUiState.Idle,
                is MapUiState.RecoverableError,
                -> {
                    IdleState(
                        onStartRequested = { viewModel.startActivity(context) },
                        onSignInRequested = { viewModel.signIn(context) },
                        onSignOutRequested = { viewModel.signOut() },
                        onQRRequested = { showQRScanSheet = true },
                        modifier = modifier,
                        isSignedIn = currentUser is User.RegisteredUser,
                    )
                }

                is MapUiState.Ongoing -> {
                    // Set the current activity.
                    val ongoingUiState = (uiState as MapUiState.Ongoing)
                    OngoingState(
                        activity = ongoingUiState.activity,
                        organizer = ongoingUiState.organizer,
                        participantIds = ongoingUiState.participantIds,
                        locations = ongoingUiState.locations,
                        onStopRequested = viewModel::leaveActivity,
                        onQRRequested = { showQRDisplaySheet = true },
                        onTrackUserRequested = {
                            Log.d("MapScreen", "Requested zoom on user")
                            mapLibreMap?.let { map ->
                                lastLocation?.let {
                                    zoomOnUser(
                                        map = map,
                                        location = it,
                                    )
                                }
                            }
                        },
                        onTrackGroupRequested = {
                            Log.d("MapScreen", "Requested zoom on group")
                            mapLibreMap?.let {
                                zoomOnGroup(
                                    map = it,
                                    participantLocations = participantLocations,
                                    currentLocation = lastLocation,
                                )
                            }
                        },
                        onAttentionRequest = viewModel::requestAttention,
                        isOrganizer = ongoingUiState.organizer.id == currentUser?.id,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModalAlert(text: String, onDismissRequest: () -> Unit) {
    BasicAlertDialog(
        onDismissRequest = onDismissRequest,
    ) {
        Surface(
            modifier = Modifier
                .wrapContentWidth()
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.large,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = text,
                )
                Spacer(modifier = Modifier.height(24.dp))
                TextButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("Dismiss")
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
            modifier = Modifier
                .wrapContentWidth()
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.large,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = message)
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.align(Alignment.End)) {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                    TextButton(onClick = onRetry) { Text("Try again") }
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
