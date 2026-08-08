/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.feature.map.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nohex.itur.core.data.health.BackendServiceIds
import com.nohex.itur.core.domain.id.IturActivityId
import com.nohex.itur.core.domain.model.User
import com.nohex.itur.core.ui.components.IturProgressIndicator
import com.nohex.itur.feature.map.ui.components.help.HelpSheet
import com.nohex.itur.feature.map.ui.components.map.BlockingState
import com.nohex.itur.feature.map.ui.components.map.ErrorState
import com.nohex.itur.feature.map.ui.components.map.IdleState
import com.nohex.itur.feature.map.ui.components.map.MapLibreView
import com.nohex.itur.feature.map.ui.components.map.NoMapView
import com.nohex.itur.feature.map.ui.components.map.OngoingState
import com.nohex.itur.feature.map.ui.components.qrdisplay.QRDisplaySheet
import com.nohex.itur.feature.map.ui.components.qrscan.QRScanSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.maplibre.android.maps.MapLibreMap

/**
 * A composable with a map. The buttons displayed on the map are placed by the UI state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    viewModel: MapViewModel = hiltViewModel(),
    locationPermissionCheck: (Context) -> Boolean = ::hasFineLocationPermission,
    openGlEsSupportCheck: (Context) -> OpenGlEsSupport = ::checkOpenGlEsSupport,
    qrScanSheet: @Composable (
        onDismissRequest: () -> Unit,
        onScanSuccess: (String) -> Unit,
    ) -> Unit = { onDismissRequest, onScanSuccess ->
        QRScanSheet(
            onDismissRequest = onDismissRequest,
            onScanSuccess = onScanSuccess,
        )
    },
) {
    val context = LocalContext.current
    val isInspection = LocalInspectionMode.current
    val openGlEsSupport = remember(context, openGlEsSupportCheck) {
        openGlEsSupportCheck(context)
    }

    val uiState by viewModel.uiState.collectAsState()
    val backendAvailability by viewModel.backendAvailability.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    // The current user.
    val currentUser by viewModel.currentUser.collectAsState()
    // The identifier of the activity displayed on the map, when there is one.
    val ongoingActivityId by viewModel.ongoingActivityId.collectAsState()
    // The identifier of the organiser of the activity displayed on the map, when there is one.
    val organizerId by viewModel.organizerId.collectAsState()
    // A reference to the MapLibre map.
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    // Initial positioning is a one-shot action. Keep it across configuration changes so a user
    // who has panned the map is not pulled back to their position after rotation.
    var centeredOnInitialLocation by rememberSaveable { mutableStateOf(false) }
    // The positions of the participants.
    val participantLocations by viewModel.participantLocations.collectAsState()
    // The last location of the device.
    val lastLocation by viewModel.lastLocation.observeAsState()
    // The most recent operator broadcast, shown as a snackbar alongside the system notification.
    val latestBroadcast by viewModel.latestBroadcast.collectAsState()
    // Whether the QR sheet is showing.
    var showQRDisplaySheet by remember { mutableStateOf(false) }
    var showQRScanSheet by remember { mutableStateOf(false) }
    var showHelpSheet by remember { mutableStateOf(false) }
    var localMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Show a snackbar when any state carries a user-facing message.
    val displayMessage = when (val state = uiState) {
        is MapUiState.Idle -> state.message
        is MapUiState.Error -> state.message
        else -> localMessage
    } ?: localMessage

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.startBackendMonitoring()
                Lifecycle.Event.ON_STOP -> viewModel.stopBackendMonitoring()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            viewModel.startBackendMonitoring()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopBackendMonitoring()
        }
    }

    // Preserve the map and local controls during outages. Each operation is disabled only by a
    // service it actually requires; the host owns the persistent route to service diagnostics.
    val failingServiceIds = backendAvailability.failingServices.mapTo(mutableSetOf()) { it.id }
    val authenticationActionsEnabled = BackendServiceIds.FIREBASE_AUTH !in failingServiceIds
    val activityActionsEnabled = BackendServiceIds.FIREBASE_FIRESTORE !in failingServiceIds

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
        displayMessage?.let {
            snackbarHostState.showSnackbar(it)
            if (localMessage == it) localMessage = null
        }
    }
    LaunchedEffect(latestBroadcast) {
        latestBroadcast?.let { snackbarHostState.showSnackbar("Alert: ${it.message}") }
    }

    // Permissions.

    // Request camera permission, if needed.
    var cameraPermissionGranted by remember { mutableStateOf(false) }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        cameraPermissionGranted = isGranted
        if (!isGranted) {
            showQRScanSheet = false
            localMessage = "Camera access is required to scan an activity QR code"
        }
    }
    // Request location permissions, if needed.
    var locationPermissionGranted by remember {
        mutableStateOf(locationPermissionCheck(context))
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        locationPermissionGranted = isGranted
    }

    // Location collection follows the visible screen, including the idle state. The ViewModel
    // only publishes fixes while an activity is ongoing, so acquiring the startup fix does not
    // expose an idle participant's position to a backend.
    DisposableEffect(lifecycleOwner, viewModel, locationPermissionGranted) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (locationPermissionGranted) {
                    viewModel.startLocationUpdates(context)
                }
                Lifecycle.Event.ON_STOP -> viewModel.stopLocationUpdates()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (locationPermissionGranted &&
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        ) {
            viewModel.startLocationUpdates(context)
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopLocationUpdates()
        }
    }

    // Request location permission when entering the map screen.
    LaunchedEffect(Unit) {
        if (openGlEsSupport.isSupported && !locationPermissionGranted) {
            // Commit the branded blocking state before Android places its permission dialog over
            // the activity. Two frames make this deterministic even on slower Android 10 devices.
            withFrameNanos { }
            withFrameNanos { }
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // Zoom once when both the rendered map and the first device fix are available. Subsequent
    // fixes deliberately leave the camera alone so normal pan/zoom gestures remain respected.
    LaunchedEffect(mapLibreMap, lastLocation, centeredOnInitialLocation) {
        centeredOnInitialLocation = centerOnInitialLocationIfReady(
            map = mapLibreMap,
            location = lastLocation,
            alreadyCentered = centeredOnInitialLocation,
        )
    }

    // Request notification permission (Android 13+) so operator broadcasts (UC-ACTIVITY-007)
    // can be delivered as system notifications, not just the in-app banner above.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {}
    LaunchedEffect(locationPermissionGranted) {
        if (locationPermissionGranted &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
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

    // Poll for operator broadcasts (UC-ACTIVITY-007) while an activity is ongoing. Tied to
    // composition rather than the ViewModel so it naturally stops when the screen leaves or
    // ongoingActivityId changes.
    LaunchedEffect(ongoingActivityId) {
        if (ongoingActivityId != null) {
            while (isActive) {
                viewModel.pollBroadcastsOnce()
                delay(BROADCAST_POLL_INTERVAL_MS)
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

        if (cameraPermissionGranted) {
            qrScanSheet(
                { showQRScanSheet = false },
                { code ->
                    IturActivityId.from(code)?.let {
                        viewModel.joinActivity(activityId = it, context = context)
                        showQRScanSheet = false
                    }
                },
            )
        }
    }

    if (showHelpSheet) {
        (uiState as? MapUiState.Ongoing)?.let { ongoingUiState ->
            HelpSheet(
                onDismissRequest = { showHelpSheet = false },
                isOrganizer = ongoingUiState.organizer.id == currentUser?.id,
            )
        }
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
            if (!openGlEsSupport.isSupported && !isInspection) {
                ErrorState(
                    guidance = "This device does not provide the graphics support required by the map.",
                    message = "OpenGL ES 3.0 or newer is required; this device reports " +
                        "${openGlEsSupport.reportedVersion}.",
                    modifier = modifier,
                )
            } else if (!locationPermissionGranted) {
                LocationPermissionRequired()
            } else {
                // Do not show the map in previews.
                if (isInspection) {
                    NoMapView()
                } else {
                    MapLibreView(
                        styleUrl = viewModel.mapStyleConfig.styleUrl,
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
                            authenticationActionsEnabled = authenticationActionsEnabled,
                            activityActionsEnabled = activityActionsEnabled,
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
                            onHelpRequested = { showHelpSheet = true },
                            isOrganizer = ongoingUiState.organizer.id == currentUser?.id,
                            activityActionsEnabled = activityActionsEnabled,
                        )
                    }
                }
            }
        }
    }
}

private fun hasFineLocationPermission(context: Context): Boolean = ContextCompat.checkSelfPermission(
    context,
    Manifest.permission.ACCESS_FINE_LOCATION,
) == PackageManager.PERMISSION_GRANTED

@Composable
private fun LocationPermissionRequired() {
    BlockingState(
        title = "Location permission required",
        message = "Allow location access to show the map and share your position during an activity.",
    )
}

/**
 * A non-dismissible overlay shown whenever one or more required services are unavailable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackendUnavailableDialog(
    failingServiceNames: List<String>,
    countdown: Int?,
    onRetryNow: () -> Unit,
    onExit: () -> Unit,
) {
    // Empty lambda: tapping outside or pressing back does nothing.
    BasicAlertDialog(onDismissRequest = {}) {
        Surface(
            modifier = Modifier
                .wrapContentWidth()
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.large,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Service unavailable",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Failed connection to ${failingServiceNames.joinToString()}.")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = countdown?.let { "Retrying in ${it}s\u2026" } ?: "Checking\u2026",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.align(Alignment.End)) {
                    TextButton(onClick = onExit) { Text("Exit") }
                    TextButton(onClick = onRetryNow) { Text("Retry now") }
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

private const val BROADCAST_POLL_INTERVAL_MS = 15_000L
