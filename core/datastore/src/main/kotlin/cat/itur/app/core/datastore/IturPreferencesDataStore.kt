/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.core.datastore

import androidx.datastore.core.DataStore
import cat.itur.app.core.model.UserSettings
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class IturPreferencesDataStore @Inject constructor(
    private val iturPreferences: DataStore<IturPreferences>,
    private val emailCipher: EmailCipher,
) {
    val preferences = iturPreferences.data
        .map {
            UserSettings(
                email = emailCipher.decrypt(it.user_email),
                participantDisplayName = it.participant_display_name.takeIf(String::isNotBlank),
            )
        }

    /**
     * @throws java.io.IOException if the update could not be persisted; callers decide how to
     * surface that failure rather than have it silently dropped here.
     */
    suspend fun setUserEmail(userEmail: String) {
        val encryptedEmail = emailCipher.encrypt(userEmail)
        iturPreferences.updateData { currentPreferences ->
            currentPreferences.copy(user_email = encryptedEmail)
        }
    }

    /** Uses DataStore's transaction so concurrent processes generate at most one winner. */
    @Suppress("MaxLineLength")
    suspend fun getOrCreateParticipantDisplayName(generate: () -> String): String = iturPreferences.updateData { currentPreferences ->
        if (currentPreferences.participant_display_name.isNotBlank()) {
            currentPreferences
        } else {
            currentPreferences.copy(participant_display_name = generate())
        }
    }.participant_display_name

    suspend fun setParticipantDisplayName(name: String) {
        iturPreferences.updateData { currentPreferences ->
            currentPreferences.copy(participant_display_name = name)
        }
    }
}
