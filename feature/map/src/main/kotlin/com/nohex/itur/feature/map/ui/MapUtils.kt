/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.feature.map.ui

import android.location.Location
import android.util.Log
import com.nohex.itur.core.model.ParticipantLocation
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap

private const val PADDING: Int = 100

/**
 * Zoom in on the current device's location.
 */
internal fun zoomOnUser(
    map: MapLibreMap,
    location: Location,
) {
    Log.d("ZoomOnUser", "Zooming on user")
    val latLng = LatLng(location.latitude, location.longitude)
    map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15.0))
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
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(points[0], 15.0))
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
