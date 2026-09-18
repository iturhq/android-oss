/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.core.datastore

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

private class FakeIturPreferencesStore(
    initial: IturPreferences = IturPreferences(),
) : DataStore<IturPreferences> {
    private val state = MutableStateFlow(initial)
    override val data: Flow<IturPreferences> = state

    override suspend fun updateData(
        transform: suspend (t: IturPreferences) -> IturPreferences,
    ): IturPreferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}

/** A reversible, non-identity cipher so tests can tell "encrypted" apart from "plaintext". */
private class FakeEmailCipher : EmailCipher {
    override fun encrypt(plainText: String): String = if (plainText.isEmpty()) "" else "enc:" + plainText.reversed()

    @Suppress("MaxLineLength")
    override fun decrypt(storedValue: String): String = if (storedValue.isEmpty()) "" else storedValue.removePrefix("enc:").reversed()
}

private class FailingIturPreferencesStore(
    private val failure: IOException,
) : DataStore<IturPreferences> {
    override val data: Flow<IturPreferences> = flow { throw failure }

    override suspend fun updateData(
        transform: suspend (t: IturPreferences) -> IturPreferences,
    ): IturPreferences = throw failure
}

private class FailingEmailCipher(
    private val failure: IllegalStateException,
) : EmailCipher {
    override fun encrypt(plainText: String): String = throw failure

    override fun decrypt(storedValue: String): String = throw failure
}

class IturPreferencesDataStoreTest {

    @Test
    fun `GIVEN a set email WHEN reading the backing store directly THEN the raw value is encrypted, not plaintext`() {
        runBlocking {
            val backingStore = FakeIturPreferencesStore()
            val dataStore = IturPreferencesDataStore(backingStore, FakeEmailCipher())

            dataStore.setUserEmail("person@example.com")

            val persisted = backingStore.data.first().user_email
            assertNotEquals("person@example.com", persisted)
            assertEquals("enc:moc.elpmaxe@nosrep", persisted)
        }
    }

    @Test
    fun `GIVEN a set email WHEN reading preferences THEN it is transparently decrypted`() = runBlocking {
        val backingStore = FakeIturPreferencesStore()
        val dataStore = IturPreferencesDataStore(backingStore, FakeEmailCipher())

        dataStore.setUserEmail("person@example.com")

        assertEquals("person@example.com", dataStore.preferences.first().email)
    }

    @Test
    fun `GIVEN no email has been set WHEN reading preferences THEN it is an empty string, not a decryption failure`() {
        runBlocking {
            val dataStore = IturPreferencesDataStore(FakeIturPreferencesStore(), FakeEmailCipher())

            assertEquals("", dataStore.preferences.first().email)
        }
    }

    @Test
    fun `GIVEN no persisted participant name WHEN creating one THEN it is stored and returned`() = runBlocking {
        val dataStore = IturPreferencesDataStore(FakeIturPreferencesStore(), FakeEmailCipher())

        assertEquals(
            "Blue Falcon",
            dataStore.getOrCreateParticipantDisplayName { "Blue Falcon" },
        )
        assertEquals("Blue Falcon", dataStore.preferences.first().participantDisplayName)
    }

    @Test
    fun `GIVEN a persisted participant name WHEN creating one THEN it retains the persisted winner`() = runBlocking {
        val generatedNames = mutableListOf<String>()
        val dataStore = IturPreferencesDataStore(
            FakeIturPreferencesStore(IturPreferences(participant_display_name = "Existing name")),
            FakeEmailCipher(),
        )

        val result = dataStore.getOrCreateParticipantDisplayName {
            generatedNames += "Should not be generated"
            "Should not be generated"
        }

        assertEquals("Existing name", result)
        assertEquals(emptyList(), generatedNames)
    }

    @Test
    fun `GIVEN a participant name WHEN replacing it THEN preferences exposes the replacement`() = runBlocking {
        val dataStore = IturPreferencesDataStore(FakeIturPreferencesStore(), FakeEmailCipher())

        dataStore.setParticipantDisplayName("Renamed participant")

        assertEquals("Renamed participant", dataStore.preferences.first().participantDisplayName)
    }

    @Test
    fun `GIVEN a persistence write failure WHEN saving an email THEN it is not silently swallowed`() = runBlocking {
        val expected = IOException("disk unavailable")
        val dataStore = IturPreferencesDataStore(FailingIturPreferencesStore(expected), FakeEmailCipher())

        val exception = kotlin.test.assertFailsWith<IOException> {
            dataStore.setUserEmail("person@example.com")
        }

        assertEquals(expected, exception)
    }

    @Test
    fun `GIVEN a persistence read failure WHEN observing preferences THEN it is surfaced`() = runBlocking {
        val expected = IOException("file is unreadable")
        val dataStore = IturPreferencesDataStore(FailingIturPreferencesStore(expected), FakeEmailCipher())

        val exception = kotlin.test.assertFailsWith<IOException> {
            dataStore.preferences.first()
        }

        assertEquals(expected, exception)
    }

    @Test
    fun `GIVEN an encryption failure WHEN saving an email THEN the backing store remains unchanged`() = runBlocking {
        val expected = IllegalStateException("keystore unavailable")
        val backingStore = FakeIturPreferencesStore()
        val dataStore = IturPreferencesDataStore(backingStore, FailingEmailCipher(expected))

        val exception = kotlin.test.assertFailsWith<IllegalStateException> {
            dataStore.setUserEmail("person@example.com")
        }

        assertEquals(expected, exception)
        assertEquals(IturPreferences(), backingStore.data.first())
    }

    @Test
    fun `GIVEN a decryption failure WHEN observing preferences THEN it is surfaced`() = runBlocking {
        val expected = IllegalStateException("keystore unavailable")
        val dataStore = IturPreferencesDataStore(
            FakeIturPreferencesStore(IturPreferences(user_email = "ciphertext")),
            FailingEmailCipher(expected),
        )

        val exception = kotlin.test.assertFailsWith<IllegalStateException> {
            dataStore.preferences.first()
        }

        assertEquals(expected, exception)
    }
}
