/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.observability

/**
 * Boots crash reporting / performance monitoring for the current flavor. `prod` binds Firebase
 * Crashlytics and Performance Monitoring; `local` binds a no-op so the credential-free emulator
 * build never links or calls into Firebase observability code.
 */
interface ObservabilityInitializer {
    fun initialize()
}
