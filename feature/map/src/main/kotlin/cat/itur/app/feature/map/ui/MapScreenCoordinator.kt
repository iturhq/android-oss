/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map.ui

import android.content.Context
import android.location.Location
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import cat.itur.app.core.data.health.BackendServiceIds
import cat.itur.app.core.domain.id.IturActivityId
import cat.itur.app.core.domain.id.UserId
import cat.itur.app.core.domain.model.User
import cat.itur.app.core.model.ParticipantLocation
import cat.itur.app.feature.map.ui.components.help.HelpAnchorRegistry
import cat.itur.app.feature.map.ui.components.help.LocalHelpAnchorRegistry
import org.maplibre.android.maps.MapLibreMap

internal data class MapEnvironment(
    val context: Context,
    val isInspection: Boolean,
    val openGlEsSupport: OpenGlEsSupport,
    val modifier: Modifier,
    val locationPermissionRequest: (((Boolean) -> Unit) -> Unit)?,
)

internal data class MapPresentation(
    val uiState: MapUiState,
    val currentUser: User?,
    val ongoingActivityId: IturActivityId?,
    val organizerId: UserId?,
    val participantLocations: List<ParticipantLocation>,
    val lastLocation: Location?,
    val latestBroadcastMessage: String?,
    val signInPresentation: SignInFailurePresentation?,
    val authenticationActionsEnabled: Boolean,
    val activityActionsEnabled: Boolean,
)

internal class MapInteractionState(
    locationPermissionState: MutableState<Boolean>,
    centeredOnInitialLocationState: MutableState<Boolean>,
) {
    var mapLibreMap by mutableStateOf<MapLibreMap?>(null)
    var mapViewportHeightPixels by mutableIntStateOf(0)
    var recentLocations by mutableStateOf(emptyList<RecentLocation>())
    var hasManualZoomOverride by mutableStateOf(false)
    var cameraTrackingMode by mutableStateOf(CameraTrackingMode.NONE)
    var centeredOnInitialLocation by centeredOnInitialLocationState
    var isDirectionOfTravel by mutableStateOf(false)
    var showQrDisplaySheet by mutableStateOf(false)
    var showQrScanSheet by mutableStateOf(false)
    var showHelp by mutableStateOf(false)
    var localMessage by mutableStateOf<String?>(null)
    var cameraPermissionGranted by mutableStateOf(false)
    var locationPermissionGranted by locationPermissionState
    var locationPermissionRequests by mutableIntStateOf(0)

    fun recordLocation(location: Location) {
        recentLocations = appendRecentLocation(recentLocations, location)
    }

    fun toggleCameraTracking(requested: CameraTrackingMode) {
        cameraTrackingMode = cameraTrackingMode.toggle(requested)
        if (cameraTrackingMode != CameraTrackingMode.NONE) hasManualZoomOverride = false
    }

    fun stopCameraTrackingForManualZoom() {
        hasManualZoomOverride = true
        cameraTrackingMode = cameraTrackingMode.stopForManualZoom()
    }

    fun stopCameraTracking() {
        cameraTrackingMode = CameraTrackingMode.NONE
    }
}

@Composable
internal fun MapScreenCoordinator(
    modifier: Modifier,
    viewModel: MapViewModel,
    locationPermissionCustomization: LocationPermissionCustomization,
    openGlEsSupportCheck: (Context) -> OpenGlEsSupport,
    qrCustomization: QrCustomization,
) {
    val environment = RememberMapEnvironment(
        openGlEsSupportCheck,
        modifier,
        locationPermissionCustomization.request,
    )
    val presentation = CollectMapPresentation(viewModel)
    val interaction = RememberMapInteractionState(
        environment.context,
        locationPermissionCustomization.check,
    )
    val snackbarHostState = remember { SnackbarHostState() }
    val helpAnchorRegistry = remember { HelpAnchorRegistry() }

    MapScreenEffects(viewModel, environment, presentation, interaction, snackbarHostState)
    CompositionLocalProvider(LocalHelpAnchorRegistry provides helpAnchorRegistry) {
        MapTransientSurfaces(viewModel, environment, presentation, interaction, qrCustomization)
        MapScaffold(viewModel, environment, presentation, interaction, snackbarHostState)
    }
}

@Composable
private fun RememberMapEnvironment(
    openGlEsSupportCheck: (Context) -> OpenGlEsSupport,
    modifier: Modifier,
    locationPermissionRequest: (((Boolean) -> Unit) -> Unit)?,
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
        locationPermissionRequest = locationPermissionRequest,
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
    val signInPresentation by viewModel.signInPresentation.collectAsState()
    val failingServices = backendAvailability.failingServices.mapTo(mutableSetOf()) { it.id }

    return MapPresentation(
        uiState = uiState,
        currentUser = currentUser,
        ongoingActivityId = ongoingActivityId,
        organizerId = organizerId,
        participantLocations = participantLocations,
        lastLocation = lastLocation,
        latestBroadcastMessage = latestBroadcast?.message,
        signInPresentation = signInPresentation,
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
