/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map.ui

import android.location.Location
import android.util.Log
import cat.itur.app.core.model.ParticipantLocation
import cat.itur.app.feature.map.ui.components.map.DIRECTION_OF_TRAVEL_POINTER_FRACTION
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin

private const val PADDING: Int = 100
internal const val MIN_SELF_FRAMING_DISTANCE_METERS = 50.0
internal const val MAX_SELF_FRAMING_DISTANCE_METERS = 300.0
private const val SPEED_TO_FRAMING_DISTANCE = 25.0
private const val RECENT_LOCATION_WINDOW_MILLIS = 10_000L
private const val MAX_RECENT_LOCATIONS = 3
private const val DEFAULT_VIEWPORT_HEIGHT_PIXELS = 1_000
private const val EARTH_METERS_PER_PIXEL_AT_ZOOM_ZERO = 156543.03392
private const val MAX_MAP_ZOOM = 22.0
private const val EARTH_RADIUS_METERS = 6_371_000.0
private const val DEGREES_PER_HALF_TURN = 180.0
private const val HALF_TURN = 2.0
private const val MILLIS_PER_SECOND_DOUBLE = 1_000.0
private const val SINGLE_LOCATION_ZOOM = 15.0

internal data class RecentLocation(
    val latitude: Double,
    val longitude: Double,
    val timeMillis: Long,
    val speedMetersPerSecond: Float?,
    val bearingDegrees: Float?,
)

internal data class MapPoint(val latitude: Double, val longitude: Double)

/**
 * Adds a fresh location sample while keeping the most recent three reports within the last ten
 * seconds. The bounded history makes speed framing responsive without reacting to one noisy fix.
 */
internal fun appendRecentLocation(
    history: List<RecentLocation>,
    location: Location,
): List<RecentLocation> = appendRecentLocation(history, location.asRecentLocation())

internal fun appendRecentLocation(
    history: List<RecentLocation>,
    sample: RecentLocation,
): List<RecentLocation> {
    if (history.lastOrNull() == sample) return history
    val newestTime = sample.timeMillis
    return (history + sample)
        .filter {
            newestTime <= 0L ||
                it.timeMillis <= 0L ||
                newestTime - it.timeMillis <= RECENT_LOCATION_WINDOW_MILLIS
        }
        .takeLast(MAX_RECENT_LOCATIONS)
}

/** Maps recent motion to the required 50–300 metre user framing band. */
internal fun selfFramingDistanceMeters(recentLocations: List<RecentLocation>): Double {
    val speed = recentAverageSpeedMetersPerSecond(recentLocations)
    return (MIN_SELF_FRAMING_DISTANCE_METERS + speed * SPEED_TO_FRAMING_DISTANCE)
        .coerceIn(MIN_SELF_FRAMING_DISTANCE_METERS, MAX_SELF_FRAMING_DISTANCE_METERS)
}

/**
 * Converts a distance from the pointer to the useful map edge into a MapLibre zoom level.
 * North-up uses the distance on both sides of the centred pointer; direction-of-travel reserves
 * the upper 80% of the viewport for the route ahead of its lower pointer anchor.
 */
internal fun selfFramingZoom(
    latitude: Double,
    viewportHeightPixels: Int,
    framingDistanceMeters: Double,
    isDirectionOfTravel: Boolean,
): Double {
    val verticalSpanMeters = if (isDirectionOfTravel) {
        framingDistanceMeters / DIRECTION_OF_TRAVEL_POINTER_FRACTION
    } else {
        framingDistanceMeters * 2
    }
    val viewportHeight = viewportHeightPixels.takeIf { it > 0 } ?: DEFAULT_VIEWPORT_HEIGHT_PIXELS
    val scale = EARTH_METERS_PER_PIXEL_AT_ZOOM_ZERO *
        cos(latitude * PI / DEGREES_PER_HALF_TURN) * viewportHeight
    return (ln(scale / verticalSpanMeters) / ln(2.0)).coerceIn(0.0, MAX_MAP_ZOOM)
}

/**
 * Moves the camera centre ahead of the GPS point in direction-of-travel mode, leaving the desired
 * [framingDistanceMeters] visible before the pointer. North-up keeps the pointer centred.
 */
internal fun selfFramingTarget(
    location: RecentLocation,
    recentLocations: List<RecentLocation>,
    framingDistanceMeters: Double,
    isDirectionOfTravel: Boolean,
): MapPoint {
    val centredTarget = MapPoint(location.latitude, location.longitude)
    val bearing = locationBearing(location, recentLocations)
    if (!isDirectionOfTravel || bearing == null) return centredTarget
    val verticalSpan = framingDistanceMeters / DIRECTION_OF_TRAVEL_POINTER_FRACTION
    val centreOffset = (framingDistanceMeters - verticalSpan / HALF_TURN).coerceAtLeast(0.0)
    return MapGeometry.destination(location.latitude, location.longitude, bearing, centreOffset)
}

/**
 * Applies the startup camera move once both prerequisites exist and returns the new one-shot
 * state. Keeping this transition separate makes the first-fix contract deterministic to test
 * without constructing a MapView in a local unit test.
 */
internal fun centerOnInitialLocationIfReady(
    map: MapLibreMap?,
    location: Location?,
    alreadyCentered: Boolean,
    center: (MapLibreMap, Location) -> Unit = ::zoomOnUser,
): Boolean {
    if (alreadyCentered || map == null || location == null) return alreadyCentered
    center(map, location)
    return true
}

/**
 * Zoom in on the current device's location.
 */
internal fun zoomOnUser(
    map: MapLibreMap,
    location: Location,
    recentLocations: List<RecentLocation> = listOf(location.asRecentLocation()),
    viewportHeightPixels: Int = DEFAULT_VIEWPORT_HEIGHT_PIXELS,
    isDirectionOfTravel: Boolean = false,
) {
    Log.d("ZoomOnUser", "Zooming on user")
    val currentLocation = location.asRecentLocation()
    val locations = recentLocations.ifEmpty { listOf(currentLocation) }
    val framingDistance = selfFramingDistanceMeters(locations)
    val target = selfFramingTarget(currentLocation, locations, framingDistance, isDirectionOfTravel)
    val zoom = selfFramingZoom(
        latitude = location.latitude,
        viewportHeightPixels = viewportHeightPixels,
        framingDistanceMeters = framingDistance,
        isDirectionOfTravel = isDirectionOfTravel,
    )
    map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(target.latitude, target.longitude), zoom))
    Log.d("ZoomOnUser", "Map should be showing the user")
}

/**
 * Zoom out so that all participants are visible.
 *
 * [currentLocation] is included in the bounds so the organiser's position is
 * always accounted for, even when they are the only person in the activity and
 * their location has not yet been written to [participantLocations].
 */
internal fun zoomOnGroup(
    map: MapLibreMap,
    participantLocations: List<ParticipantLocation>,
    currentLocation: Location?,
) {
    Log.d("ZoomOnGroup", "Zooming on group")
    val points = buildList {
        participantLocations.mapTo(this) { LatLng(it.location.latitude, it.location.longitude) }
        currentLocation?.let { add(LatLng(it.latitude, it.longitude)) }
    }

    when {
        points.isEmpty() -> Log.d("ZoomOnGroup", "No locations available, skipping")
        points.size == 1 -> {
            // Single point – animate to it at a fixed zoom level.
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(points[0], SINGLE_LOCATION_ZOOM))
            Log.d("ZoomOnGroup", "Map should be showing single location")
        }
        else -> try {
            val bounds = LatLngBounds.Builder().includes(points).build()
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, PADDING))
            Log.d("ZoomOnGroup", "Map should be showing the group")
        } catch (e: Exception) {
            Log.e("MapScreen", "Zoom on group failed", e)
        }
    }
}

private fun recentAverageSpeedMetersPerSecond(locations: List<RecentLocation>): Double {
    val recent = locations.takeLast(MAX_RECENT_LOCATIONS)
    if (recent.size >= 2) {
        val first = recent.first()
        val last = recent.last()
        val durationMillis = last.timeMillis - first.timeMillis
        if (durationMillis > 0L) {
            val distance = recent.zipWithNext().sumOf { (from, to) -> MapGeometry.distanceBetween(from, to) }
            return distance / (durationMillis / MILLIS_PER_SECOND_DOUBLE)
        }
    }
    val reportedSpeeds = recent.mapNotNull(RecentLocation::speedMetersPerSecond)
    return reportedSpeeds.average().takeUnless(Double::isNaN) ?: 0.0
}

private fun Location.asRecentLocation(): RecentLocation = RecentLocation(
    latitude = latitude,
    longitude = longitude,
    timeMillis = time,
    speedMetersPerSecond = speed.takeIf { hasSpeed() },
    bearingDegrees = bearing.takeIf { hasBearing() },
)

private fun locationBearing(location: RecentLocation, recentLocations: List<RecentLocation>): Double? = when {
    location.bearingDegrees != null -> location.bearingDegrees.toDouble()
    recentLocations.size >= 2 -> MapGeometry.bearingBetween(
        recentLocations[recentLocations.lastIndex - 1],
        location,
    )
    else -> null
}

private object MapGeometry {
    fun distanceBetween(from: RecentLocation, to: RecentLocation): Double {
        val latitudeDelta = radians(to.latitude - from.latitude)
        val longitudeDelta = radians(to.longitude - from.longitude)
        val fromLatitude = radians(from.latitude)
        val toLatitude = radians(to.latitude)
        val a = sin(latitudeDelta / HALF_TURN) * sin(latitudeDelta / HALF_TURN) +
            cos(fromLatitude) * cos(toLatitude) *
            sin(longitudeDelta / HALF_TURN) * sin(longitudeDelta / HALF_TURN)
        return EARTH_RADIUS_METERS * HALF_TURN * kotlin.math.atan2(
            kotlin.math.sqrt(a),
            kotlin.math.sqrt(1.0 - a),
        )
    }

    fun bearingBetween(from: RecentLocation, to: RecentLocation): Double {
        val longitudeDelta = radians(to.longitude - from.longitude)
        val fromLatitude = radians(from.latitude)
        val toLatitude = radians(to.latitude)
        return kotlin.math.atan2(
            sin(longitudeDelta) * cos(toLatitude),
            cos(fromLatitude) * sin(toLatitude) -
                sin(fromLatitude) * cos(toLatitude) * cos(longitudeDelta),
        ) * DEGREES_PER_HALF_TURN / PI
    }

    fun destination(
        latitude: Double,
        longitude: Double,
        bearingDegrees: Double,
        distanceMeters: Double,
    ): MapPoint {
        val angularDistance = distanceMeters / EARTH_RADIUS_METERS
        val bearing = radians(bearingDegrees)
        val startLatitude = radians(latitude)
        val startLongitude = radians(longitude)
        val targetLatitude = kotlin.math.asin(
            sin(startLatitude) * cos(angularDistance) +
                cos(startLatitude) * sin(angularDistance) * cos(bearing),
        )
        val targetLongitude = startLongitude + kotlin.math.atan2(
            sin(bearing) * sin(angularDistance) * cos(startLatitude),
            cos(angularDistance) - sin(startLatitude) * sin(targetLatitude),
        )
        return MapPoint(
            targetLatitude * DEGREES_PER_HALF_TURN / PI,
            targetLongitude * DEGREES_PER_HALF_TURN / PI,
        )
    }

    private fun radians(degrees: Double): Double = degrees * PI / DEGREES_PER_HALF_TURN
}
