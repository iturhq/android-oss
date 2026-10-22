/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.core.data.repository

import android.content.Context
import android.content.SharedPreferences
import cat.itur.app.core.model.GpxLine
import cat.itur.app.core.model.GpxPoint
import cat.itur.app.core.model.GpxTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-local track state. Persistence is intentionally owned by the import slice. */
interface GpxTrackRepository {
    val track: StateFlow<GpxTrack?>
    suspend fun setTrack(track: GpxTrack?)
}

open class InMemoryGpxTrackRepository : GpxTrackRepository {
    private val state = MutableStateFlow<GpxTrack?>(null)
    override val track: StateFlow<GpxTrack?> = state.asStateFlow()
    override suspend fun setTrack(track: GpxTrack?) {
        state.value = track
    }
}

/** Private on-device storage for the one locally imported track. */
class PersistedGpxTrackRepository(context: Context) : GpxTrackRepository {
    private val preferences: SharedPreferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(preferences.getString(TRACK, null)?.toTrack())

    override val track: StateFlow<GpxTrack?> = state.asStateFlow()

    override suspend fun setTrack(track: GpxTrack?) {
        preferences.edit().putString(TRACK, track?.encode()).commit()
        state.value = track
    }

    private fun GpxTrack.encode() = lines.joinToString(";") { line ->
        line.points.joinToString("|") { point -> "${point.latitude},${point.longitude}" }
    }

    private fun String.toTrack(): GpxTrack? = runCatching {
        GpxTrack(
            split(';').filter(String::isNotBlank).map { line ->
                GpxLine(
                    line.split('|').map { point ->
                        val (latitude, longitude) = point.split(',', limit = 2)
                        GpxPoint(latitude.toDouble(), longitude.toDouble())
                    },
                )
            },
        )
    }.getOrNull()

    private companion object {
        const val PREFERENCES = "gpx_track"
        const val TRACK = "track"
    }
}
