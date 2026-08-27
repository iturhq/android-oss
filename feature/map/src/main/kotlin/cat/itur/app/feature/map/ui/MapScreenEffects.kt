/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
internal fun MapScreenEffects(
    viewModel: MapViewModel,
    environment: MapEnvironment,
    presentation: MapPresentation,
    interaction: MapInteractionState,
    snackbarHostState: SnackbarHostState,
) {
    BackendMonitoringEffect(viewModel)
    ParticipantLocationMonitoringEffect(viewModel)
    MessageEffects(presentation, interaction, snackbarHostState)
    LocationUpdatesEffect(viewModel, environment.context, interaction.locationPermissionGranted)
    LocationPermissionEffect(environment, interaction)
    InitialCenteringEffect(presentation, interaction)
    NotificationPermissionEffect(environment.context, interaction.locationPermissionGranted)
    ActivityStateEffects(viewModel, environment.context, presentation)
}

@Composable
private fun ParticipantLocationMonitoringEffect(viewModel: MapViewModel) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.startParticipantLocationMonitoring()
                Lifecycle.Event.ON_STOP -> viewModel.stopParticipantLocationMonitoring()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            viewModel.startParticipantLocationMonitoring()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopParticipantLocationMonitoring()
        }
    }
}

@Composable
private fun BackendMonitoringEffect(viewModel: MapViewModel) {
    val lifecycleOwner = LocalLifecycleOwner.current
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
}

@Composable
private fun MessageEffects(
    presentation: MapPresentation,
    interaction: MapInteractionState,
    snackbarHostState: SnackbarHostState,
) {
    val displayMessage = when (val state = presentation.uiState) {
        is MapUiState.Idle -> state.message
        is MapUiState.Error -> state.message
        else -> interaction.localMessage
    } ?: interaction.localMessage

    LaunchedEffect(displayMessage) {
        displayMessage?.let {
            snackbarHostState.showSnackbar(it)
            if (interaction.localMessage == it) interaction.localMessage = null
        }
    }
    LaunchedEffect(presentation.latestBroadcastMessage) {
        presentation.latestBroadcastMessage?.let {
            snackbarHostState.showSnackbar("Alert: $it")
        }
    }
}

@Composable
private fun LocationUpdatesEffect(
    viewModel: MapViewModel,
    context: Context,
    locationPermissionGranted: Boolean,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
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
}

@Composable
private fun LocationPermissionEffect(
    environment: MapEnvironment,
    interaction: MapInteractionState,
) {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { interaction.locationPermissionGranted = it }
    LaunchedEffect(Unit) {
        if (environment.openGlEsSupport.isSupported && !interaction.locationPermissionGranted) {
            withFrameNanos { }
            withFrameNanos { }
            launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
}

@Composable
private fun InitialCenteringEffect(
    presentation: MapPresentation,
    interaction: MapInteractionState,
) {
    LaunchedEffect(
        interaction.mapLibreMap,
        presentation.lastLocation,
        interaction.centeredOnInitialLocation,
    ) {
        interaction.centeredOnInitialLocation = centerOnInitialLocationIfReady(
            map = interaction.mapLibreMap,
            location = presentation.lastLocation,
            alreadyCentered = interaction.centeredOnInitialLocation,
        )
    }
}

@Composable
private fun NotificationPermissionEffect(context: Context, locationPermissionGranted: Boolean) {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {}
    LaunchedEffect(locationPermissionGranted) {
        if (locationPermissionGranted &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
private fun ActivityStateEffects(
    viewModel: MapViewModel,
    context: Context,
    presentation: MapPresentation,
) {
    LaunchedEffect(presentation.ongoingActivityId) {
        presentation.ongoingActivityId?.let {
            viewModel.triggerOngoingState(it, context)
        } ?: run {
            if (presentation.uiState !is MapUiState.Idle) viewModel.triggerIdleState()
        }
    }
    LaunchedEffect(presentation.ongoingActivityId) {
        if (presentation.ongoingActivityId != null) {
            while (isActive) {
                viewModel.pollBroadcastsOnce()
                delay(BROADCAST_POLL_INTERVAL_MS)
            }
        }
    }
}

private const val BROADCAST_POLL_INTERVAL_MS = 15_000L
