/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.core.data.repository

import cat.itur.app.core.datastore.IturPreferencesDataStore
import cat.itur.app.core.model.UserSettings
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

internal class OfflineFirstUserSettingsRepository @Inject constructor(
    private val iturPreferencesDataSource: IturPreferencesDataStore,
) : UserSettingsRepository {
    override val userSettings: Flow<UserSettings> = iturPreferencesDataSource.preferences
    override suspend fun setUserEmail(email: String) = iturPreferencesDataSource.setUserEmail(email)

    @Suppress("MaxLineLength")
    override suspend fun getOrCreateParticipantDisplayName(generate: () -> String): String = iturPreferencesDataSource.getOrCreateParticipantDisplayName(generate)

    @Suppress("MaxLineLength")
    override suspend fun setParticipantDisplayName(name: String) = iturPreferencesDataSource.setParticipantDisplayName(name)
}
