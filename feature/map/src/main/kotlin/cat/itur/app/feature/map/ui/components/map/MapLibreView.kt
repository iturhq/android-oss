/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map.ui.components.map

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import cat.itur.app.core.domain.id.UserId
import cat.itur.app.core.model.ParticipantLocation
import cat.itur.app.feature.map.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.maplibre.android.location.LocationComponent
import org.maplibre.android.location.LocationComponentActivationOptions.builder
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.engine.LocationEngineRequest
import org.maplibre.android.location.engine.LocationEngineRequest.PRIORITY_HIGH_ACCURACY
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMap.OnScaleListener
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconOpacity
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

private const val ORGANIZER_LAYER = "organizer-layer"
private const val ORGANIZER_SOURCE = "organizer-source"
private const val PARTICIPANT_LAYER = "participants-layer"
private const val PARTICIPANT_SOURCE = "participants-source"
private const val MARKER_OTHER = "marker-other"
private const val MARKER_ORGANIZER = "marker-organizer"
private const val RECENCY_REFRESH_INTERVAL_MILLIS = 1_000L
private const val MILLIS_PER_SECOND = 1_000L
private const val CURRENT_MARKER_OPACITY = 1f
private const val AGING_MARKER_OPACITY = 0.6f
private const val STALE_MARKER_OPACITY = 0.3f
private const val UNKNOWN_AGE_MARKER_OPACITY = 0.45f
internal const val DIRECTION_OF_TRAVEL_POINTER_FRACTION = 0.8
private const val IMMEDIATE_CAMERA_TRANSITION_DURATION_MS = 0L
const val PERSISTENT_MAP_NATIVE_VIEW_TAG = "itur-persistent-map-native-view"

internal data class LocationRecencyThresholds(
    val agingAfterMillis: Long = 15_000L,
    val staleAfterMillis: Long = 30_000L,
)

internal val LocalLocationRecencyThresholds = compositionLocalOf { LocationRecencyThresholds() }

internal enum class LocationRecency {
    CURRENT,
    AGING,
    STALE,
    UNKNOWN,
}

data class MapLibreViewInput(
    val styleUrl: String,
    val isActivityOngoing: Boolean,
    val locationPermissionGranted: Boolean,
    val currentUserId: UserId?,
    val organizerId: UserId?,
    val participantLocations: List<ParticipantLocation>,
    val isDirectionOfTravel: Boolean = false,
)

data class MapLibreViewCallbacks(
    val onMapReady: (MapLibreMap) -> Unit = {},
    val onViewportHeightChanged: (Int) -> Unit = {},
    val onManualZoomChanged: () -> Unit = {},
    val onStyleLoadFailed: () -> Unit = {},
    val onStyleLoadSucceeded: () -> Unit = {},
)

private data class MapLocationTrackingState(
    val isActivityOngoing: Boolean,
    val isDirectionOfTravel: Boolean,
    val locationPermissionGranted: Boolean,
    val styleLoaded: Boolean,
    val map: MapLibreMap?,
    val viewportHeight: Int,
) {
    companion object {
        fun from(
            input: MapLibreViewInput,
            styleLoaded: Boolean,
            map: MapLibreMap?,
            viewportHeight: Int,
        ) = MapLocationTrackingState(
            isActivityOngoing = input.isActivityOngoing,
            isDirectionOfTravel = input.isDirectionOfTravel,
            locationPermissionGranted = input.locationPermissionGranted,
            styleLoaded = styleLoaded,
            map = map,
            viewportHeight = viewportHeight,
        )
    }
}

private data class MapMarkerState(
    val styleLoaded: Boolean,
    val map: MapLibreMap?,
    val nowMillis: Long,
    val recencyThresholds: LocationRecencyThresholds,
)

private data class MapStyleCallbacks(
    val onMapAvailable: (MapLibreMap) -> Unit,
    val onStyleLoaded: () -> Unit,
    val onMapReady: (MapLibreMap) -> Unit,
)

/**
 * An implementation of the map view provided by MapLibre.
 */
@Composable
fun MapLibreView(
    input: MapLibreViewInput,
    modifier: Modifier = Modifier,
    callbacks: MapLibreViewCallbacks = MapLibreViewCallbacks(),
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val recencyThresholds = LocalLocationRecencyThresholds.current
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    val mapView = remember { MapViewHost.create(context) }
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleLoaded by remember { mutableStateOf(false) }
    var locationComponent by remember { mutableStateOf<LocationComponent?>(null) }
    var mapViewportHeight by remember { mutableStateOf(0) }
    val accessibilityDescription = input.participantLocations
        .filter { it.userId != input.currentUserId }
        .joinToString(", ", prefix = "Participant locations. ") {
            it.accessibleAge(nowMillis, recencyThresholds)
        }
    MapViewHost.UpdateRecencyClock { nowMillis = it }

    MapRenderingCallbacks(mapView, callbacks.onStyleLoadFailed, callbacks.onStyleLoadSucceeded)
    ManualZoomEffect(mapLibreMap, callbacks.onManualZoomChanged)
    MapLifecycleEffect(lifecycle, mapView)
    MapLocationComponentEffect(
        state = MapLocationTrackingState.from(input, styleLoaded, mapLibreMap, mapViewportHeight),
        context = context,
        locationComponent = locationComponent,
        onLocationComponentChanged = { locationComponent = it },
    )
    MapStyleInitializer.SetupEffect(
        mapView = mapView,
        context = context,
        styleUrl = input.styleUrl,
        callbacks = MapStyleCallbacks(
            onMapAvailable = { mapLibreMap = it },
            onStyleLoaded = { styleLoaded = true },
            onMapReady = callbacks.onMapReady,
        ),
    )
    MapMarkers.Effect(
        input = input,
        state = MapMarkerState(
            styleLoaded = styleLoaded,
            map = mapLibreMap,
            nowMillis = nowMillis,
            recencyThresholds = recencyThresholds,
        ),
        mapView = mapView,
        accessibilityDescription = accessibilityDescription,
    )

    MapViewHost.Content(
        mapView = mapView,
        modifier = modifier,
        accessibilityDescription = accessibilityDescription,
        onViewportHeightChanged = {
            mapViewportHeight = it
            callbacks.onViewportHeightChanged(it)
        },
    )
}

@Composable
private fun MapRenderingCallbacks(
    mapView: MapView,
    onStyleLoadFailed: () -> Unit,
    onStyleLoadSucceeded: () -> Unit,
) {
    DisposableEffect(mapView, onStyleLoadFailed, onStyleLoadSucceeded) {
        val failureListener = MapView.OnDidFailLoadingMapListener { onStyleLoadFailed() }
        val renderingListener = MapView.OnDidFinishRenderingMapListener { fully ->
            if (fully) onStyleLoadSucceeded()
        }
        val shaderFailureListener = MapView.OnShaderCompileFailedListener { _, _, _ ->
            onStyleLoadFailed()
        }
        mapView.addOnDidFailLoadingMapListener(failureListener)
        mapView.addOnDidFinishRenderingMapListener(renderingListener)
        mapView.addOnShaderCompileFailedListener(shaderFailureListener)
        onDispose {
            mapView.removeOnDidFailLoadingMapListener(failureListener)
            mapView.removeOnDidFinishRenderingMapListener(renderingListener)
            mapView.removeOnShaderCompileFailedListener(shaderFailureListener)
        }
    }
}

@Composable
private fun ManualZoomEffect(map: MapLibreMap?, onManualZoomChanged: () -> Unit) {
    DisposableEffect(map, onManualZoomChanged) {
        val currentMap = map ?: return@DisposableEffect onDispose {}
        val scaleListener = object : OnScaleListener {
            override fun onScaleBegin(detector: org.maplibre.android.gestures.StandardScaleGestureDetector) = Unit

            override fun onScale(detector: org.maplibre.android.gestures.StandardScaleGestureDetector) = Unit

            override fun onScaleEnd(detector: org.maplibre.android.gestures.StandardScaleGestureDetector) {
                onManualZoomChanged()
            }
        }
        currentMap.addOnScaleListener(scaleListener)
        onDispose { currentMap.removeOnScaleListener(scaleListener) }
    }
}

@Composable
private fun MapLifecycleEffect(lifecycle: Lifecycle, mapView: MapView) {
    DisposableEffect(lifecycle) {
        val observer = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = mapView.onStart()
            override fun onResume(owner: LifecycleOwner) = mapView.onResume()
            override fun onPause(owner: LifecycleOwner) = mapView.onPause()
            override fun onStop(owner: LifecycleOwner) = mapView.onStop()
            override fun onDestroy(owner: LifecycleOwner) = mapView.onDestroy()
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        onDispose { lifecycle.removeObserver(observer) }
    }
}

@Composable
private fun MapLocationComponentEffect(
    state: MapLocationTrackingState,
    context: Context,
    locationComponent: LocationComponent?,
    onLocationComponentChanged: (LocationComponent) -> Unit,
) {
    @SuppressLint("MissingPermission")
    LaunchedEffect(
        state.isActivityOngoing,
        state.isDirectionOfTravel,
        state.locationPermissionGranted,
        state.styleLoaded,
        state.map,
        state.viewportHeight,
    ) {
        val map = state.map
        val style = map?.style
        if (!state.locationPermissionGranted || !state.styleLoaded) {
            locationComponent?.isLocationComponentEnabled = false
        } else if (map == null || style == null) {
            locationComponent?.isLocationComponentEnabled = false
        } else {
            val component = locationComponent ?: createLocationComponent(map, context, style).also {
                onLocationComponentChanged(it)
            }
            component.isLocationComponentEnabled = state.isActivityOngoing
            component.setCameraMode(
                if (state.isActivityOngoing && state.isDirectionOfTravel) {
                    CameraMode.TRACKING_GPS
                } else {
                    CameraMode.TRACKING_GPS_NORTH
                },
                IMMEDIATE_CAMERA_TRANSITION_DURATION_MS,
                null,
                null,
                null,
                null,
            )
            component.paddingWhileTracking(doubleArrayOf(0.0, state.trackingPadding(), 0.0, 0.0))
        }
    }
}

private fun MapLocationTrackingState.trackingPadding(): Double = if (
    isActivityOngoing && isDirectionOfTravel
) {
    viewportHeight * (2 * DIRECTION_OF_TRAVEL_POINTER_FRACTION - 1)
} else {
    0.0
}

private object MapStyleInitializer {
    @Composable
    fun SetupEffect(
        mapView: MapView,
        context: Context,
        styleUrl: String,
        callbacks: MapStyleCallbacks,
    ) {
        LaunchedEffect(Unit) {
            mapView.getMapAsync { map ->
                callbacks.onMapAvailable(map)
                map.setStyle(styleUrl) { style ->
                    addMapMarkers(style, context)
                    addMapMarkerLayers(style)
                    configureMapGestures(map)
                    callbacks.onStyleLoaded()
                    callbacks.onMapReady(map)
                }
            }
        }
    }

    private fun addMapMarkers(style: Style, context: Context) {
        vectorToBitmap(context, R.drawable.ic_location_other)?.let { style.addImage(MARKER_OTHER, it) }
        vectorToBitmap(context, R.drawable.ic_location_organiser)?.let { style.addImage(MARKER_ORGANIZER, it) }
    }

    private fun addMapMarkerLayers(style: Style) {
        style.addSource(GeoJsonSource(ORGANIZER_SOURCE, FeatureCollection.fromFeatures(emptyList<Feature>())))
        style.addLayer(
            SymbolLayer(ORGANIZER_LAYER, ORGANIZER_SOURCE).withProperties(
                iconImage(Expression.get("marker")),
                iconOpacity(Expression.get("opacity")),
            ),
        )
        style.addSource(GeoJsonSource(PARTICIPANT_SOURCE, FeatureCollection.fromFeatures(emptyList<Feature>())))
        style.addLayer(
            SymbolLayer(PARTICIPANT_LAYER, PARTICIPANT_SOURCE).withProperties(
                iconImage(Expression.get("marker")),
                iconOpacity(Expression.get("opacity")),
            ),
        )
    }

    private fun configureMapGestures(map: MapLibreMap) = map.uiSettings.apply {
        isScrollGesturesEnabled = true
        isZoomGesturesEnabled = true
        isRotateGesturesEnabled = false
        isTiltGesturesEnabled = false
    }
}

private object MapMarkers {
    @Composable
    fun Effect(
        input: MapLibreViewInput,
        state: MapMarkerState,
        mapView: MapView,
        accessibilityDescription: String,
    ) {
        LaunchedEffect(
            input.participantLocations,
            state.styleLoaded,
            state.map,
            state.nowMillis,
            state.recencyThresholds,
        ) {
            state.map?.style?.takeIf { state.styleLoaded }?.let { style ->
                if (updateOrganizerMarker(style, input, state)) {
                    updateParticipantMarkers(style, input, state)
                }
            }
            mapView.contentDescription = accessibilityDescription
        }
    }

    private fun updateOrganizerMarker(style: Style, input: MapLibreViewInput, state: MapMarkerState): Boolean {
        val source = style.getSourceAs<GeoJsonSource>(ORGANIZER_SOURCE) ?: return false
        Log.d("MapLibreView", "Updating organiser feature collection")
        input.participantLocations.firstOrNull { it.userId == input.organizerId }?.let { organizerLocation ->
            source.setGeoJson(
                FeatureCollection.fromFeature(
                    Feature.fromGeometry(
                        Point.fromLngLat(organizerLocation.location.longitude, organizerLocation.location.latitude),
                    ).apply {
                        addStringProperty("id", organizerLocation.userId.value)
                        addStringProperty("marker", MARKER_ORGANIZER)
                        addNumberProperty(
                            "opacity",
                            organizerLocation.markerOpacity(state.nowMillis, state.recencyThresholds),
                        )
                    },
                ),
            )
        }
        return true
    }

    private fun updateParticipantMarkers(style: Style, input: MapLibreViewInput, state: MapMarkerState) {
        style.getSourceAs<GeoJsonSource>(PARTICIPANT_SOURCE)?.let { source ->
            Log.d("MapLibreView", "Updating participant feature collection")
            source.setGeoJson(
                FeatureCollection.fromFeatures(
                    input.participantLocations
                        .filter { it.userId != input.organizerId && it.userId != input.currentUserId }
                        .map { location ->
                            Feature.fromGeometry(
                                Point.fromLngLat(location.location.longitude, location.location.latitude),
                            ).apply {
                                addStringProperty("label", location.userName)
                                addStringProperty("id", location.userId.value)
                                addStringProperty("marker", MARKER_OTHER)
                                addNumberProperty(
                                    "opacity",
                                    location.markerOpacity(state.nowMillis, state.recencyThresholds),
                                )
                            }
                        },
                ),
            )
        }
    }
}

private object MapViewHost {
    fun create(context: Context): MapView {
        val options = MapLibreMapOptions.createFromAttributes(context).textureMode(true)
        return MapView(context, options).also {
            it.tag = PERSISTENT_MAP_NATIVE_VIEW_TAG
            it.onCreate(null)
        }
    }

    @Composable
    fun Content(
        mapView: MapView,
        modifier: Modifier,
        accessibilityDescription: String,
        onViewportHeightChanged: (Int) -> Unit,
    ) {
        AndroidView(
            modifier = modifier
                .onSizeChanged { onViewportHeightChanged(it.height) }
                .semantics { contentDescription = accessibilityDescription },
            factory = { mapView },
        )
    }

    @Composable
    fun UpdateRecencyClock(onTick: (Long) -> Unit) {
        LaunchedEffect(Unit) {
            while (isActive) {
                onTick(System.currentTimeMillis())
                delay(RECENCY_REFRESH_INTERVAL_MILLIS)
            }
        }
    }
}

internal fun ParticipantLocation.recency(
    nowMillis: Long,
    thresholds: LocationRecencyThresholds = LocationRecencyThresholds(),
): LocationRecency {
    val ageMillis = recordedAt?.let { (nowMillis - it.time).coerceAtLeast(0L) }
        ?: return LocationRecency.UNKNOWN
    return when {
        ageMillis >= thresholds.staleAfterMillis -> LocationRecency.STALE
        ageMillis >= thresholds.agingAfterMillis -> LocationRecency.AGING
        else -> LocationRecency.CURRENT
    }
}

internal fun ParticipantLocation.accessibleAge(
    nowMillis: Long,
    thresholds: LocationRecencyThresholds = LocationRecencyThresholds(),
): String {
    val seconds = recordedAt?.let {
        ((nowMillis - it.time).coerceAtLeast(0L) / MILLIS_PER_SECOND)
    }
    return when (recency(nowMillis, thresholds)) {
        LocationRecency.CURRENT -> "$userName location is current"
        LocationRecency.AGING -> "$userName location updated about $seconds seconds ago"
        LocationRecency.STALE -> "$userName location is stale, updated about $seconds seconds ago"
        LocationRecency.UNKNOWN -> "$userName location age is unknown"
    }
}

private fun ParticipantLocation.markerOpacity(
    nowMillis: Long,
    thresholds: LocationRecencyThresholds,
): Float = when (recency(nowMillis, thresholds)) {
    LocationRecency.CURRENT -> CURRENT_MARKER_OPACITY
    LocationRecency.AGING -> AGING_MARKER_OPACITY
    LocationRecency.STALE -> STALE_MARKER_OPACITY
    LocationRecency.UNKNOWN -> UNKNOWN_AGE_MARKER_OPACITY
}

private fun vectorToBitmap(context: Context, drawableId: Int): Bitmap? = ResourcesCompat.getDrawable(
    context.resources,
    drawableId,
    null,
)?.toBitmap(width = 64, height = 64)

/**
 * Create a location component that tracks the user's position.
 */
@SuppressLint("MissingPermission")
private fun createLocationComponent(
    map: MapLibreMap,
    context: Context,
    style: Style,
): LocationComponent {
    val locationComponentActivationOptions = builder(context, style)
        .locationComponentOptions(
            LocationComponentOptions.builder(context)
                .pulseEnabled(true)
                .build(),
        )
        .locationEngineRequest(
            LocationEngineRequest.Builder(5000)
                .setFastestInterval(1000)
                .setPriority(PRIORITY_HIGH_ACCURACY)
                .build(),
        )
        .useDefaultLocationEngine(true)
        .build()

    return map.locationComponent.apply {
        activateLocationComponent(locationComponentActivationOptions)
        cameraMode = CameraMode.TRACKING_GPS_NORTH
        // Enable only for ongoing activities.
        isLocationComponentEnabled = false
    }
}
