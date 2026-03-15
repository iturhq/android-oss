/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.datastore

import android.util.Log
import androidx.datastore.core.DataStore
import com.nohex.itur.core.model.UserSettings
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject

class IturPreferencesDataStore @Inject constructor(
    private val iturPreferences: DataStore<IturPreferences>,
) {
    val preferences = iturPreferences.data
        .map {
            UserSettings(
                email = it.user_email,
            )
        }

    suspend fun setUserEmail(userEmail: String) {
        try {
            iturPreferences.updateData { currentPreferences ->
                currentPreferences.copy(user_email = userEmail)
            }
        } catch (e: IOException) {
            Log.e(
                "IturPreferences",
                "Failed to update user settings for email",
                e,
            )
        }
    }
}
