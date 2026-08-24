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
import androidx.compose.ui.platform.testTag
import com.nohex.itur.core.domain.model.User
import com.nohex.itur.core.ui.components.IturProgressIndicator
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
        NoMapView(modifier = environment.modifier.testTag("persistent_map_surface"))
    } else {
        MapLibreView(
            styleUrl = viewModel.mapStyleConfig.styleUrl,
            isActivityOngoing = presentation.ongoingActivityId != null,
            locationPermissionGranted = interaction.locationPermissionGranted,
            organizerId = presentation.organizerId,
            currentUserId = presentation.currentUser?.id,
            participantLocations = presentation.participantLocations,
            modifier = environment.modifier
                .fillMaxSize()
                .testTag("persistent_map_surface"),
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
        presentation.currentUser == null -> IturProgressIndicator(
            label = "Preparing map...",
            modifier = environment.modifier.testTag("map_state_loading"),
        )
        presentation.uiState is MapUiState.Loading ->
            IturProgressIndicator(
                label = "Preparing activity...",
                modifier = environment.modifier.testTag("map_state_loading"),
            )
        presentation.uiState is MapUiState.Ongoing ->
            OngoingControls(
                viewModel,
                presentation,
                interaction,
                environment,
                "map_state_ongoing",
            )
        presentation.uiState is MapUiState.Error ->
            IdleControls(viewModel, presentation, interaction, environment, "map_state_error")
        presentation.uiState is MapUiState.RecoverableError ->
            IdleControls(
                viewModel,
                presentation,
                interaction,
                environment,
                "map_state_recoverable_error",
            )
        else -> IdleControls(viewModel, presentation, interaction, environment, "map_state_idle")
    }
}

@Composable
private fun IdleControls(
    viewModel: MapViewModel,
    presentation: MapPresentation,
    interaction: MapInteractionState,
    environment: MapEnvironment,
    stateTag: String,
) {
    IdleState(
        onStartRequested = { viewModel.startActivity(environment.context) },
        onSignInRequested = { viewModel.signIn(environment.context) },
        onSignOutRequested = viewModel::signOut,
        onQRRequested = { interaction.showQrScanSheet = true },
        onHelpRequested = { interaction.showHelp = true },
        modifier = environment.modifier.testTag(stateTag),
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
    stateTag: String,
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
        modifier = environment.modifier.testTag(stateTag),
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
