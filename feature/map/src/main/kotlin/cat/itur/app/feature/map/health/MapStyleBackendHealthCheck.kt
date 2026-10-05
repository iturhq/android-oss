/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map.health

import cat.itur.app.core.data.health.BackendHealthCheck
import cat.itur.app.core.data.health.BackendService
import cat.itur.app.core.data.health.BackendServiceIds
import cat.itur.app.feature.map.config.MapStyleConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

/** Bounded metadata reachability for the same map-style service the renderer reports against. */
class MapStyleBackendHealthCheck internal constructor(
    private val config: MapStyleConfig,
    private val downloadMetadata: suspend (String) -> Unit,
) : BackendHealthCheck {
    @Inject
    constructor(config: MapStyleConfig) : this(config, ::downloadMapStyleMetadata)

    override val service = BackendService(BackendServiceIds.MAP_STYLE, "Map style")
    override val diagnosticOrder = MAP_STYLE_DIAGNOSTIC_ORDER
    override val successDetail = "Style metadata downloaded"

    override suspend fun probe() {
        val styleUrl = config.styleUrl
        require(styleUrl.isNotBlank()) { "Map style URL is blank" }
        require(!styleUrl.endsWith("key=")) { "Map style credential is not configured" }
        downloadMetadata(styleUrl)
    }
}

private suspend fun downloadMapStyleMetadata(styleUrl: String) = withContext(Dispatchers.IO) {
    val connection = (URL(styleUrl).openConnection() as HttpURLConnection).apply {
        connectTimeout = HTTP_TIMEOUT_MILLIS
        readTimeout = HTTP_TIMEOUT_MILLIS
        requestMethod = "GET"
    }
    try {
        require(connection.responseCode in HTTP_SUCCESS_MIN_CODE..HTTP_SUCCESS_MAX_CODE) {
            "Map style endpoint did not return success"
        }
    } finally {
        connection.disconnect()
    }
}

private const val HTTP_TIMEOUT_MILLIS = 4_000
private const val MAP_STYLE_DIAGNOSTIC_ORDER = 60
private const val HTTP_SUCCESS_MIN_CODE = 200
private const val HTTP_SUCCESS_MAX_CODE = 299
