/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.feature.map.ui

import android.app.ActivityManager
import android.content.Context

internal const val REQUIRED_OPEN_GL_ES_VERSION = 0x00030000

data class OpenGlEsSupport(
    val isSupported: Boolean,
    val reportedVersion: String,
)

internal fun checkOpenGlEsSupport(context: Context): OpenGlEsSupport {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    return openGlEsSupport(activityManager?.deviceConfigurationInfo?.reqGlEsVersion ?: 0)
}

internal fun openGlEsSupport(reportedVersion: Int): OpenGlEsSupport {
    val major = reportedVersion shr 16 and 0xffff
    val minor = reportedVersion and 0xffff
    return OpenGlEsSupport(
        isSupported = reportedVersion >= REQUIRED_OPEN_GL_ES_VERSION,
        reportedVersion = "$major.$minor",
    )
}
