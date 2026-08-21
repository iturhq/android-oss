/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cat.itur.app.feature.map.R

/** Localizes app-authored map messages while preserving backend-authored details verbatim. */
@Composable
internal fun localizedMapMessage(message: String): String {
    val dynamicMessage = dynamicMapMessage(message)
    if (dynamicMessage != null) {
        return stringResource(dynamicMessage.resource, dynamicMessage.argument)
    }
    val resource = staticMapMessageResources[message] ?: return message
    return stringResource(resource)
}

private fun dynamicMapMessage(message: String): DynamicMapMessage? = dynamicMapMessagePatterns
    .firstNotNullOfOrNull { (pattern, resource) ->
        pattern.matchEntire(message)?.groupValues?.get(1)?.let { DynamicMapMessage(resource, it) }
    }

private data class DynamicMapMessage(@StringRes val resource: Int, val argument: String)

private val dynamicMapMessagePatterns = listOf(
    Regex("Activity (.+) not found") to R.string.feature_map_activity_not_found,
    Regex("Failed to join activity (.+)") to R.string.feature_map_failed_join,
    Regex("Failed to leave activity (.+)") to R.string.feature_map_failed_leave,
)

private val staticMapMessageResources = mapOf(
    "Failed to start an activity" to R.string.feature_map_failed_start,
    "You're already in an activity -- leave it first" to R.string.feature_map_already_in_activity,
    "You are no longer participating in an activity" to R.string.feature_map_no_longer_participating,
    "The ongoing activity could not be resumed." to R.string.feature_map_resume_failed,
    "No Google account is available. Add an account and try again." to
        R.string.feature_map_sign_in_no_account,
    "Sign-in isn't configured for this app." to R.string.feature_map_sign_in_not_configured,
    "Sign-in is temporarily unavailable. Check your connection and try again." to
        R.string.feature_map_sign_in_unavailable,
    "Sign-in couldn't be completed. Try again." to R.string.feature_map_sign_in_failed,
)
