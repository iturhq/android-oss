/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
internal fun GpxCameraFitEffect(
    presentation: MapPresentation,
    interaction: MapInteractionState,
) {
    val density = LocalDensity.current
    val horizontalFallback = with(density) { 88.dp.roundToPx() }
    val verticalPadding = with(density) { 32.dp.roundToPx() }
    LaunchedEffect(presentation.gpxTrack, presentation.ongoingActivityId, interaction.mapLibreMap) {
        zoomOnGpxTrack(
            map = interaction.mapLibreMap ?: return@LaunchedEffect,
            track = presentation.gpxTrack,
            isActivityOngoing = presentation.ongoingActivityId != null,
            insets = cameraFitInsets(
                mapBounds = interaction.mapBounds,
                leftControlBounds = interaction.leftControlBounds,
                rightControlBounds = interaction.rightControlBounds,
                horizontalFallback = horizontalFallback,
                verticalPadding = verticalPadding,
            ),
        )
    }
}

@Composable
internal fun RecentLocationHistoryEffect(
    presentation: MapPresentation,
    interaction: MapInteractionState,
) {
    LaunchedEffect(presentation.lastLocation) {
        presentation.lastLocation?.let(interaction::recordLocation)
    }
}

@Composable
internal fun InitialCenteringEffect(
    presentation: MapPresentation,
    interaction: MapInteractionState,
) {
    LaunchedEffect(
        interaction.mapLibreMap,
        presentation.lastLocation,
        presentation.gpxTrack,
        presentation.ongoingActivityId,
        interaction.centeredOnInitialLocation,
        interaction.hasManualZoomOverride,
    ) {
        interaction.centeredOnInitialLocation = centerOnInitialLocationIfReady(
            map = interaction.mapLibreMap,
            location = presentation.lastLocation,
            alreadyCentered = interaction.centeredOnInitialLocation,
            center = { map, location ->
                val importedTrackOwnsInitialCamera =
                    presentation.gpxTrack != null && presentation.ongoingActivityId == null
                if (!interaction.hasManualZoomOverride && !importedTrackOwnsInitialCamera) {
                    zoomOnUser(
                        map = map,
                        location = location,
                        recentLocations = interaction.recentLocations,
                        viewportHeightPixels = interaction.mapViewportHeightPixels,
                        isDirectionOfTravel = interaction.isDirectionOfTravel,
                    )
                }
            },
        )
    }
}

@Composable
internal fun ContinuousCameraTrackingEffect(
    presentation: MapPresentation,
    interaction: MapInteractionState,
) {
    val density = LocalDensity.current
    val horizontalFallback = with(density) { 88.dp.roundToPx() }
    val verticalPadding = with(density) { 32.dp.roundToPx() }
    LaunchedEffect(
        interaction.cameraTrackingMode,
        interaction.mapLibreMap,
        presentation.lastLocation,
        presentation.participantLocations,
        interaction.recentLocations,
        interaction.mapViewportHeightPixels,
        interaction.isDirectionOfTravel,
        interaction.mapBounds,
        interaction.leftControlBounds,
        interaction.rightControlBounds,
    ) {
        val map = interaction.mapLibreMap ?: return@LaunchedEffect
        when (interaction.cameraTrackingMode) {
            CameraTrackingMode.NONE -> Unit
            CameraTrackingMode.USER -> presentation.lastLocation?.let { location ->
                zoomOnUser(
                    map = map,
                    location = location,
                    recentLocations = interaction.recentLocations,
                    viewportHeightPixels = interaction.mapViewportHeightPixels,
                    isDirectionOfTravel = interaction.isDirectionOfTravel,
                )
            }
            CameraTrackingMode.GROUP -> zoomOnGroup(
                map = map,
                participantLocations = presentation.participantLocations,
                currentLocation = presentation.lastLocation,
                insets = cameraFitInsets(
                    mapBounds = interaction.mapBounds,
                    leftControlBounds = interaction.leftControlBounds,
                    rightControlBounds = interaction.rightControlBounds,
                    horizontalFallback = horizontalFallback,
                    verticalPadding = verticalPadding,
                ),
            )
        }
    }
}
