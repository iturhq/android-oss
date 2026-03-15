/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.feature.map.ui.components.map

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nohex.itur.core.domain.id.UserId
import com.nohex.itur.core.model.ParticipantLocation
import com.nohex.itur.feature.map.BuildConfig
import com.nohex.itur.feature.map.R
import org.maplibre.android.location.LocationComponent
import org.maplibre.android.location.LocationComponentActivationOptions.builder
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.engine.LocationEngineRequest
import org.maplibre.android.location.engine.LocationEngineRequest.PRIORITY_HIGH_ACCURACY
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

private const val STYLE_URL =
    "https://api.maptiler.com/maps/streets/style.json?key=${BuildConfig.MAPTILER_API_KEY}"

private const val ORGANIZER_LAYER = "organizer-layer"
private const val ORGANIZER_SOURCE = "organizer-source"
private const val PARTICIPANT_LAYER = "participants-layer"
private const val PARTICIPANT_SOURCE = "participants-source"
private const val MARKER_OTHER = "marker-other"
private const val MARKER_ORGANIZER = "marker-organizer"

/**
 * An implementation of the map view provided by MapLibre.
 */
@Composable
fun MapLibreView(
    isActivityOngoing: Boolean,
    currentUserId: UserId?,
    organizerId: UserId?,
    participantLocations: List<ParticipantLocation>,
    modifier: Modifier = Modifier,
    onMapReady: (MapLibreMap) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    // Keep a reference to the MapView and the map itself.
    // - TextureView mode avoids a SIGSEGV in MapLibre's native RenderThread on
    //   emulators using SwiftShader (the SurfaceView path crashes deterministically
    //   with fault addr 0x18 right after eglMakeCurrent).
    // - onCreate must be called here, before the view is attached to the hierarchy,
    //   so the NativeMapView is initialised before the render thread starts.
    val mapView = remember {
        val options = MapLibreMapOptions.createFromAttributes(context).textureMode(true)
        MapView(context, options).also { it.onCreate(null) }
    }
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleLoaded by remember { mutableStateOf(false) }
    var locationComponent by remember { mutableStateOf<LocationComponent?>(null) }

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
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }

    @SuppressLint("MissingPermission")
    LaunchedEffect(isActivityOngoing) {
        locationComponent?.apply {
            isLocationComponentEnabled = isActivityOngoing
        }
    }

    // Set up the map once it's ready.
    LaunchedEffect(Unit) {
        mapView.getMapAsync { map ->
            mapLibreMap = map

            map.setStyle(STYLE_URL) { style ->

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
                        iconImage(MARKER_ORGANIZER),
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
                        iconImage(MARKER_OTHER),
                    ),
                )

                locationComponent = createLocationComponent(map, context, style)

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
    LaunchedEffect(participantLocations, styleLoaded, mapLibreMap) {
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
                                    // TODO:
                                    // addStringProperty("label", it.userName)
                                    addStringProperty("id", organizerLocation.userId.value)
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
                                    }
                                },
                        ),
                    )
                }
            }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { mapView },
    )
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
        cameraMode = CameraMode.TRACKING
        // Enable only for ongoing activities.
        isLocationComponentEnabled = false
    }
}
