/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.feature.map.config

/**
 * How often [com.nohex.itur.feature.map.ui.MapViewModel] requests a device-location fix while an
 * activity is ongoing. Supplied via Hilt so the consuming application can tune it (battery life
 * versus tracking precision) without changing this module.
 */
data class LocationUpdateConfig(
    val updateIntervalMillis: Long,
)
