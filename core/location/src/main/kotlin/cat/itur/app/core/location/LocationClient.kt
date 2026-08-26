/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.core.location

import android.location.Location

/**
 * Platform-neutral boundary for device location updates.
 *
 * Play Services request, callback, priority, and result types deliberately stay behind this
 * contract so feature modules can substitute a deterministic fake without depending on GMS.
 */
interface LocationClient {
    fun requestUpdates(intervalMillis: Long, onLocation: (Location) -> Unit)

    fun removeUpdates(onLocation: (Location) -> Unit)
}
