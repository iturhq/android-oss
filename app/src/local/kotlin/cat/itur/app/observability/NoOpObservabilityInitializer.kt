/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.observability

import javax.inject.Inject

/** Local emulator builds must never initialize production telemetry SDKs. */
class NoOpObservabilityInitializer @Inject constructor() : ObservabilityInitializer {
    override fun initialize() = Unit
}
