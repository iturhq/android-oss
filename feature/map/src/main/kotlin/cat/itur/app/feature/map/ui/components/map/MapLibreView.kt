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
private const val DIRECTION_OF_TRAVEL_POINTER_FRACTION = 0.8
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

/**
 * An implementation of the map view provided by MapLibre.
 */
@Composable
fun MapLibreView(
    styleUrl: String,
    isActivityOngoing: Boolean,
    locationPermissionGranted: Boolean,
    currentUserId: UserId?,
    organizerId: UserId?,
    participantLocations: List<ParticipantLocation>,
    isDirectionOfTravel: Boolean = false,
    modifier: Modifier = Modifier,
    onMapReady: (MapLibreMap) -> Unit = {},
    onViewportHeightChanged: (Int) -> Unit = {},
    onManualZoomChanged: () -> Unit = {},
    onStyleLoadFailed: () -> Unit = {},
    onStyleLoadSucceeded: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val recencyThresholds = LocalLocationRecencyThresholds.current
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }

    // Keep a reference to the MapView and the map itself.
    // - TextureView mode avoids a SIGSEGV in MapLibre's native RenderThread on
    //   emulators using SwiftShader (the SurfaceView path crashes deterministically
    //   with fault addr 0x18 right after eglMakeCurrent).
    // - onCreate must be called here, before the view is attached to the hierarchy,
    //   so the NativeMapView is initialised before the render thread starts.
    val mapView = remember {
        val options = MapLibreMapOptions.createFromAttributes(context).textureMode(true)
        MapView(context, options).also {
            it.tag = PERSISTENT_MAP_NATIVE_VIEW_TAG
            it.onCreate(null)
        }
    }
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleLoaded by remember { mutableStateOf(false) }
    var locationComponent by remember { mutableStateOf<LocationComponent?>(null) }
    var mapViewportHeight by remember { mutableStateOf(0) }
    val accessibilityDescription = participantLocations
        .filter { it.userId != currentUserId }
        .joinToString(", ", prefix = "Participant locations. ") {
            it.accessibleAge(nowMillis, recencyThresholds)
        }

    LaunchedEffect(Unit) {
        while (isActive) {
            nowMillis = System.currentTimeMillis()
            delay(RECENCY_REFRESH_INTERVAL_MILLIS)
        }
    }

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

    DisposableEffect(mapLibreMap, onManualZoomChanged) {
        val map = mapLibreMap ?: return@DisposableEffect onDispose {}
        val scaleListener = object : OnScaleListener {
            override fun onScaleBegin(detector: org.maplibre.android.gestures.StandardScaleGestureDetector) = Unit

            override fun onScale(detector: org.maplibre.android.gestures.StandardScaleGestureDetector) = Unit

            override fun onScaleEnd(detector: org.maplibre.android.gestures.StandardScaleGestureDetector) {
                onManualZoomChanged()
            }
        }
        map.addOnScaleListener(scaleListener)
        onDispose { map.removeOnScaleListener(scaleListener) }
    }

    // Forward lifecycle events to the map
    DisposableEffect(lifecycle) {
        val observer = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = mapView.onStart()
            override fun onResume(owner: LifecycleOwner) = mapView.onResume()
            override fun onPause(owner: LifecycleOwner) = mapView.onPause()
            override fun onStop(owner: LifecycleOwner) = mapView.onStop()
            override fun onDestroy(owner: LifecycleOwner) = mapView.onDestroy()
        }

        lifecycle.addObserver(observer)
        // This composable is normally added after the Activity has already reached RESUMED.
        // Lifecycle observers do not replay past events, so bring MapView to the host's current
        // state before a permission dialog (or any other overlay) can pause it again.
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            mapView.onStart()
        }
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            mapView.onResume()
        }
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }

    @SuppressLint("MissingPermission")
    LaunchedEffect(
        isActivityOngoing,
        isDirectionOfTravel,
        locationPermissionGranted,
        styleLoaded,
        mapLibreMap,
        mapViewportHeight,
    ) {
        val map = mapLibreMap
        val style = map?.style
        if (locationPermissionGranted && styleLoaded && map != null && style != null) {
            val component = locationComponent ?: createLocationComponent(map, context, style).also {
                locationComponent = it
            }
            component.isLocationComponentEnabled = isActivityOngoing
            component.setCameraMode(
                if (isActivityOngoing && isDirectionOfTravel) {
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
            component.paddingWhileTracking(
                doubleArrayOf(
                    0.0,
                    if (isActivityOngoing && isDirectionOfTravel) {
                        mapViewportHeight * (2 * DIRECTION_OF_TRAVEL_POINTER_FRACTION - 1)
                    } else {
                        0.0
                    },
                    0.0,
                    0.0,
                ),
            )
        } else {
            locationComponent?.isLocationComponentEnabled = false
        }
    }

    // Set up the map once it's ready.
    LaunchedEffect(Unit) {
        mapView.getMapAsync { map ->
            mapLibreMap = map

            map.setStyle(styleUrl) { style ->

                // Add marker for others.
                vectorToBitmap(context, R.drawable.ic_location_other)?.let {
                    style.addImage(MARKER_OTHER, it)
                }

                // Add marker for organiser.
                vectorToBitmap(context, R.drawable.ic_location_organiser)?.let {
                    style.addImage(MARKER_ORGANIZER, it)
                }

                // Create symbol layer for organiser
                style.addSource(
                    GeoJsonSource(
                        ORGANIZER_SOURCE,
                        FeatureCollection.fromFeatures(emptyList<Feature>()),
                    ),
                )
                style.addLayer(
                    SymbolLayer(ORGANIZER_LAYER, ORGANIZER_SOURCE).withProperties(
                        iconImage(Expression.get("marker")),
                        iconOpacity(Expression.get("opacity")),
                    ),
                )

                // Create symbol layer for participants.
                style.addSource(
                    GeoJsonSource(
                        PARTICIPANT_SOURCE,
                        FeatureCollection.fromFeatures(emptyList<Feature>()),
                    ),
                )
                style.addLayer(
                    SymbolLayer(PARTICIPANT_LAYER, PARTICIPANT_SOURCE).withProperties(
                        iconImage(Expression.get("marker")),
                        iconOpacity(Expression.get("opacity")),
                    ),
                )

                map.uiSettings.apply {
                    isScrollGesturesEnabled = true
                    isZoomGesturesEnabled = true
                    // Disable rotation and tilt for the time being,
                    // they may come in handy later on for a view
                    // in the direction of movement.
                    isRotateGesturesEnabled = false
                    isTiltGesturesEnabled = false
                }
                styleLoaded = true

                // The map is now ready.
                onMapReady(map)
            }
        }
    }

    // Update the GeoJsonSource when featureCollection changes
    LaunchedEffect(participantLocations, styleLoaded, mapLibreMap, nowMillis, recencyThresholds) {
        val style = mapLibreMap?.style
        if (styleLoaded && style != null) {
            style.getSourceAs<GeoJsonSource>(ORGANIZER_SOURCE)?.let { geoJsonSource ->
                Log.d("MapLibreView", "Updating organiser feature collection")

                // Add only the organiser.
                participantLocations.firstOrNull { it.userId == organizerId }
                    ?.let { organizerLocation ->
                        geoJsonSource.setGeoJson(
                            FeatureCollection.fromFeature(
                                Feature.fromGeometry(
                                    Point.fromLngLat(
                                        organizerLocation.location.longitude,
                                        organizerLocation.location.latitude,
                                    ),
                                ).apply {
                                    addStringProperty("id", organizerLocation.userId.value)
                                    addStringProperty("marker", MARKER_ORGANIZER)
                                    addNumberProperty(
                                        "opacity",
                                        organizerLocation.markerOpacity(nowMillis, recencyThresholds),
                                    )
                                },
                            ),
                        )
                    }

                // Add the participants.
                style.getSourceAs<GeoJsonSource>(PARTICIPANT_SOURCE)?.let {
                    Log.d("MapLibreView", "Updating participant feature collection")
                    it.setGeoJson(
                        FeatureCollection.fromFeatures(
                            participantLocations
                                // Do not show the organiser, it's on another layer.
                                .filter { it.userId != organizerId }
                                // Do not show the current user, the map does.
                                .filter { it.userId != currentUserId }
                                .map {
                                    Feature.fromGeometry(
                                        Point.fromLngLat(
                                            it.location.longitude,
                                            it.location.latitude,
                                        ),
                                    ).apply {
                                        addStringProperty("label", it.userName)
                                        addStringProperty("id", it.userId.value)
                                        addStringProperty("marker", MARKER_OTHER)
                                        addNumberProperty(
                                            "opacity",
                                            it.markerOpacity(nowMillis, recencyThresholds),
                                        )
                                    }
                                },
                        ),
                    )
                }
            }
        }
        mapView.contentDescription = accessibilityDescription
    }

    AndroidView(
        modifier = modifier
            .onSizeChanged {
                mapViewportHeight = it.height
                onViewportHeightChanged(it.height)
            }
            .semantics {
                contentDescription = accessibilityDescription
            },
        factory = { mapView },
    )
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
