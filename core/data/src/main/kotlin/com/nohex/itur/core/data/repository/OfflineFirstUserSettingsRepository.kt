/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.data.repository

import com.nohex.itur.core.datastore.IturPreferencesDataStore
import com.nohex.itur.core.model.UserSettings
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

internal class OfflineFirstUserSettingsRepository @Inject constructor(
    private val iturPreferencesDataSource: IturPreferencesDataStore,
) : UserSettingsRepository {
    override val userSettings: Flow<UserSettings> = iturPreferencesDataSource.preferences
    override suspend fun setUserEmail(email: String) = iturPreferencesDataSource.setUserEmail(email)
}
