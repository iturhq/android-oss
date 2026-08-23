/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.feature.map.ui

import android.app.ActivityManager
import android.content.Context

internal const val REQUIRED_OPEN_GL_ES_VERSION = 0x00030000
private const val OPEN_GL_ES_MAJOR_SHIFT = 16
private const val OPEN_GL_ES_COMPONENT_MASK = 0xffff

data class OpenGlEsSupport(
    val isSupported: Boolean,
    val reportedVersion: String,
)

internal fun checkOpenGlEsSupport(context: Context): OpenGlEsSupport {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    return openGlEsSupport(activityManager?.deviceConfigurationInfo?.reqGlEsVersion ?: 0)
}

internal fun openGlEsSupport(reportedVersion: Int): OpenGlEsSupport {
    val major = reportedVersion shr OPEN_GL_ES_MAJOR_SHIFT and OPEN_GL_ES_COMPONENT_MASK
    val minor = reportedVersion and OPEN_GL_ES_COMPONENT_MASK
    return OpenGlEsSupport(
        isSupported = reportedVersion >= REQUIRED_OPEN_GL_ES_VERSION,
        reportedVersion = "$major.$minor",
    )
}
