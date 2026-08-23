/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.feature.map.ui

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import com.nohex.itur.core.domain.model.User
import com.nohex.itur.core.ui.components.IturProgressIndicator
import com.nohex.itur.feature.map.ui.components.map.BlockingState
import com.nohex.itur.feature.map.ui.components.map.ErrorState
import com.nohex.itur.feature.map.ui.components.map.IdleState
import com.nohex.itur.feature.map.ui.components.map.MapLibreView
import com.nohex.itur.feature.map.ui.components.map.NoMapView
import com.nohex.itur.feature.map.ui.components.map.OngoingState
import com.nohex.itur.feature.map.ui.components.map.OngoingStateActions

@Composable
internal fun MapScaffold(
    viewModel: MapViewModel,
    environment: MapEnvironment,
    presentation: MapPresentation,
    interaction: MapInteractionState,
    snackbarHostState: SnackbarHostState,
) {
    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data -> Snackbar(snackbarData = data) }
        },
    ) { paddingValues ->
        Box(modifier = androidx.compose.ui.Modifier.padding(paddingValues)) {
            MapContent(viewModel, environment, presentation, interaction)
        }
    }
}

@Composable
private fun MapContent(
    viewModel: MapViewModel,
    environment: MapEnvironment,
    presentation: MapPresentation,
    interaction: MapInteractionState,
) {
    when {
        !environment.openGlEsSupport.isSupported && !environment.isInspection -> ErrorState(
            guidance = "This device does not provide the graphics support required by the map.",
            message = "OpenGL ES 3.0 or newer is required; this device reports " +
                "${environment.openGlEsSupport.reportedVersion}.",
            modifier = environment.modifier,
        )
        !interaction.locationPermissionGranted -> BlockingState(
            title = "Location permission required",
            message = "Allow location access to show the map and share your position during an activity.",
        )
        else -> MapReadyContent(viewModel, environment, presentation, interaction)
    }
}

@Composable
private fun MapReadyContent(
    viewModel: MapViewModel,
    environment: MapEnvironment,
    presentation: MapPresentation,
    interaction: MapInteractionState,
) {
    if (environment.isInspection) {
        NoMapView()
    } else {
        MapLibreView(
            styleUrl = viewModel.mapStyleConfig.styleUrl,
            isActivityOngoing = presentation.ongoingActivityId != null,
            locationPermissionGranted = interaction.locationPermissionGranted,
            organizerId = presentation.organizerId,
            currentUserId = presentation.currentUser?.id,
            participantLocations = presentation.participantLocations,
            modifier = environment.modifier.fillMaxSize(),
            onMapReady = { interaction.mapLibreMap = it },
            onStyleLoadFailed = viewModel::reportMapStyleLoadFailed,
            onStyleLoadSucceeded = viewModel::reportMapStyleLoadSucceeded,
        )
    }
    MapStateControls(viewModel, environment, presentation, interaction)
}

@Composable
private fun MapStateControls(
    viewModel: MapViewModel,
    environment: MapEnvironment,
    presentation: MapPresentation,
    interaction: MapInteractionState,
) {
    when {
        presentation.currentUser == null -> IturProgressIndicator(label = "Preparing map...")
        presentation.uiState is MapUiState.Loading ->
            IturProgressIndicator(label = "Preparing activity...")
        presentation.uiState is MapUiState.Ongoing ->
            OngoingControls(viewModel, presentation, interaction, environment)
        else -> IdleControls(viewModel, presentation, interaction, environment)
    }
}

@Composable
private fun IdleControls(
    viewModel: MapViewModel,
    presentation: MapPresentation,
    interaction: MapInteractionState,
    environment: MapEnvironment,
) {
    IdleState(
        onStartRequested = { viewModel.startActivity(environment.context) },
        onSignInRequested = { viewModel.signIn(environment.context) },
        onSignOutRequested = viewModel::signOut,
        onQRRequested = { interaction.showQrScanSheet = true },
        onHelpRequested = { interaction.showHelp = true },
        modifier = environment.modifier,
        isSignedIn = presentation.currentUser is User.RegisteredUser,
        authenticationActionsEnabled = presentation.authenticationActionsEnabled,
        activityActionsEnabled = presentation.activityActionsEnabled,
    )
}

@Composable
private fun OngoingControls(
    viewModel: MapViewModel,
    presentation: MapPresentation,
    interaction: MapInteractionState,
    environment: MapEnvironment,
) {
    val ongoingUiState = presentation.uiState as MapUiState.Ongoing
    OngoingState(
        actions = OngoingStateActions(
            onStopRequested = viewModel::leaveActivity,
            onQrRequested = { interaction.showQrDisplaySheet = true },
            onTrackUserRequested = { trackUser(presentation, interaction) },
            onTrackGroupRequested = { trackGroup(presentation, interaction) },
            onAttentionRequest = viewModel::requestAttention,
            onHelpRequested = { interaction.showHelp = true },
        ),
        isOrganizer = ongoingUiState.organizer.id == presentation.currentUser?.id,
        modifier = environment.modifier,
        activityActionsEnabled = presentation.activityActionsEnabled,
    )
}

private fun trackUser(presentation: MapPresentation, interaction: MapInteractionState) {
    Log.d("MapScreen", "Requested zoom on user")
    interaction.mapLibreMap?.let { map ->
        presentation.lastLocation?.let { zoomOnUser(map = map, location = it) }
    }
}

private fun trackGroup(presentation: MapPresentation, interaction: MapInteractionState) {
    Log.d("MapScreen", "Requested zoom on group")
    interaction.mapLibreMap?.let {
        zoomOnGroup(
            map = it,
            participantLocations = presentation.participantLocations,
            currentLocation = presentation.lastLocation,
        )
    }
}
