/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.feature.map.ui

import android.content.Context
import android.location.Location
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import com.nohex.itur.core.data.health.BackendServiceIds
import com.nohex.itur.core.domain.id.IturActivityId
import com.nohex.itur.core.domain.id.UserId
import com.nohex.itur.core.domain.model.User
import com.nohex.itur.core.model.ParticipantLocation
import org.maplibre.android.maps.MapLibreMap

internal data class MapEnvironment(
    val context: Context,
    val isInspection: Boolean,
    val openGlEsSupport: OpenGlEsSupport,
    val modifier: Modifier,
)

internal data class MapPresentation(
    val uiState: MapUiState,
    val currentUser: User?,
    val ongoingActivityId: IturActivityId?,
    val organizerId: UserId?,
    val participantLocations: List<ParticipantLocation>,
    val lastLocation: Location?,
    val latestBroadcastMessage: String?,
    val authenticationActionsEnabled: Boolean,
    val activityActionsEnabled: Boolean,
)

internal class MapInteractionState(
    locationPermissionState: MutableState<Boolean>,
    centeredOnInitialLocationState: MutableState<Boolean>,
) {
    var mapLibreMap by mutableStateOf<MapLibreMap?>(null)
    var centeredOnInitialLocation by centeredOnInitialLocationState
    var showQrDisplaySheet by mutableStateOf(false)
    var showQrScanSheet by mutableStateOf(false)
    var showHelpSheet by mutableStateOf(false)
    var localMessage by mutableStateOf<String?>(null)
    var cameraPermissionGranted by mutableStateOf(false)
    var locationPermissionGranted by locationPermissionState
}

@Composable
internal fun MapScreenCoordinator(
    modifier: Modifier,
    viewModel: MapViewModel,
    locationPermissionCheck: (Context) -> Boolean,
    openGlEsSupportCheck: (Context) -> OpenGlEsSupport,
    qrScanSheet: @Composable (onDismissRequest: () -> Unit, onScanSuccess: (String) -> Unit) -> Unit,
) {
    val environment = RememberMapEnvironment(openGlEsSupportCheck, modifier)
    val presentation = CollectMapPresentation(viewModel)
    val interaction = RememberMapInteractionState(environment.context, locationPermissionCheck)
    val snackbarHostState = remember { SnackbarHostState() }

    MapScreenEffects(viewModel, environment, presentation, interaction, snackbarHostState)
    MapTransientSurfaces(viewModel, environment, presentation, interaction, qrScanSheet)
    MapScaffold(viewModel, environment, presentation, interaction, snackbarHostState)
}

@Composable
private fun RememberMapEnvironment(
    openGlEsSupportCheck: (Context) -> OpenGlEsSupport,
    modifier: Modifier,
): MapEnvironment {
    val context = LocalContext.current
    val openGlEsSupport = remember(context, openGlEsSupportCheck) {
        openGlEsSupportCheck(context)
    }
    return MapEnvironment(
        context = context,
        isInspection = LocalInspectionMode.current,
        openGlEsSupport = openGlEsSupport,
        modifier = modifier,
    )
}

@Composable
private fun CollectMapPresentation(viewModel: MapViewModel): MapPresentation {
    val uiState by viewModel.uiState.collectAsState()
    val backendAvailability by viewModel.backendAvailability.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val ongoingActivityId by viewModel.ongoingActivityId.collectAsState()
    val organizerId by viewModel.organizerId.collectAsState()
    val participantLocations by viewModel.participantLocations.collectAsState()
    val lastLocation by viewModel.lastLocation.observeAsState()
    val latestBroadcast by viewModel.latestBroadcast.collectAsState()
    val failingServices = backendAvailability.failingServices.mapTo(mutableSetOf()) { it.id }

    return MapPresentation(
        uiState = uiState,
        currentUser = currentUser,
        ongoingActivityId = ongoingActivityId,
        organizerId = organizerId,
        participantLocations = participantLocations,
        lastLocation = lastLocation,
        latestBroadcastMessage = latestBroadcast?.message,
        authenticationActionsEnabled = BackendServiceIds.FIREBASE_AUTH !in failingServices,
        activityActionsEnabled = BackendServiceIds.FIREBASE_FIRESTORE !in failingServices,
    )
}

@Composable
private fun RememberMapInteractionState(
    context: Context,
    locationPermissionCheck: (Context) -> Boolean,
): MapInteractionState {
    val locationPermissionState = remember(context, locationPermissionCheck) {
        mutableStateOf(locationPermissionCheck(context))
    }
    val centeredOnInitialLocationState = rememberSaveable { mutableStateOf(false) }
    return remember(locationPermissionState, centeredOnInitialLocationState) {
        MapInteractionState(locationPermissionState, centeredOnInitialLocationState)
    }
}
