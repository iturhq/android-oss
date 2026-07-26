/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.datastore

import androidx.datastore.core.DataStore
import com.nohex.itur.core.model.UserSettings
import kotlinx.coroutines.flow.map
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

    /**
     * @throws java.io.IOException if the update could not be persisted; callers decide how to
     * surface that failure rather than have it silently dropped here.
     */
    suspend fun setUserEmail(userEmail: String) {
        iturPreferences.updateData { currentPreferences ->
            currentPreferences.copy(user_email = userEmail)
        }
    }
}
