/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.core.data.repository

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
