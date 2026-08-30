/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.observability

import javax.inject.Inject

/**
 * The local flavor stays fully offline and credential-free (see README): it never links or calls
 * into Firebase Crashlytics/Performance Monitoring, so this initializer is intentionally empty
 * rather than a stub that happens to do nothing today.
 */
class NoOpObservabilityInitializer @Inject constructor() : ObservabilityInitializer {
    override fun initialize() = Unit
}
