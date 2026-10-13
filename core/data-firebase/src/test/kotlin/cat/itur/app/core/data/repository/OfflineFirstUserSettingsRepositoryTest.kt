/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.core.data.repository

import cat.itur.app.core.datastore.IturPreferencesDataStore
import cat.itur.app.core.model.UserSettings
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class OfflineFirstUserSettingsRepositoryTest {
    private val dataStore = mockk<IturPreferencesDataStore> {
        every { preferences } returns flowOf(UserSettings())
    }

    @Test
    fun `user settings are delegated without changing their public values`() = runBlocking {
        val settings = UserSettings(email = "ada@example.com", participantDisplayName = "Ada")
        every { dataStore.preferences } returns flowOf(settings)
        val repository = OfflineFirstUserSettingsRepository(dataStore)

        assertEquals(settings, repository.userSettings.first())
    }

    @Test
    fun `settings mutations are delegated to the persistence boundary`() = runBlocking {
        coEvery { dataStore.setUserEmail("ada@example.com") } returns Unit
        coEvery { dataStore.getOrCreateParticipantDisplayName(any()) } returns "Ada"
        coEvery { dataStore.setParticipantDisplayName("Ada") } returns Unit
        val repository = OfflineFirstUserSettingsRepository(dataStore)

        repository.setUserEmail("ada@example.com")
        assertEquals("Ada", repository.getOrCreateParticipantDisplayName { "Ada" })
        repository.setParticipantDisplayName("Ada")

        coVerify { dataStore.setUserEmail("ada@example.com") }
        coVerify { dataStore.getOrCreateParticipantDisplayName(any()) }
        coVerify { dataStore.setParticipantDisplayName("Ada") }
    }
}
