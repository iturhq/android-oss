/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.data.repository

import com.nohex.itur.core.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A user settings repository that stores data in memory.
 */
class FakeUserSettingsRepository : UserSettingsRepository {
    private val _userSettings = MutableStateFlow(UserSettings())
    private val participantNameMutex = Mutex()

    override val userSettings: Flow<UserSettings> = _userSettings.asStateFlow()

    override suspend fun setUserEmail(email: String) {
        _userSettings.update { it.copy(email = email) }
    }

    @Suppress("MaxLineLength")
    override suspend fun getOrCreateParticipantDisplayName(generate: () -> String): String = participantNameMutex.withLock {
        _userSettings.value.participantDisplayName ?: generate().also { generated ->
            _userSettings.update { it.copy(participantDisplayName = generated) }
        }
    }

    override suspend fun setParticipantDisplayName(name: String) {
        _userSettings.update { it.copy(participantDisplayName = name) }
    }
}
