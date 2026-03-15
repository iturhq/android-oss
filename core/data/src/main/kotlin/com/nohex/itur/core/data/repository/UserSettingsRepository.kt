/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.data.repository

import com.nohex.itur.core.model.UserSettings
import kotlinx.coroutines.flow.Flow

/**
 * The application settings for the current user.
 */
interface UserSettingsRepository {
    val userSettings: Flow<UserSettings>

    /**
     * Set the authenticated user's email.
     */
    suspend fun setUserEmail(email: String)
}
