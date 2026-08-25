/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map.config

/**
 * The MapLibre style URL to render, including any tile-provider API key. Owned by the consuming
 * application, not this module -- supplied via Hilt by whichever application assembles the
 * dependency graph.
 */
data class MapStyleConfig(
    val styleUrl: String,
)
