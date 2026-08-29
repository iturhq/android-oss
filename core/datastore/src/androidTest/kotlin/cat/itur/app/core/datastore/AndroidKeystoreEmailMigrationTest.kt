/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.core.datastore

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.MultiProcessDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory

private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val TRANSFORMATION = "AES/GCM/NoPadding"

@RunWith(AndroidJUnit4::class)
class AndroidKeystoreEmailMigrationTest {

    @Test
    fun legacyPlaintextFileIsEncryptedBeforeFirstReadWithA256BitKey() = runBlocking {
        val fixture = MigrationFixture()
        try {
            fixture.writeRaw(IturPreferences(user_email = fixture.email))
            assertTrue(fixture.file.readText(Charsets.ISO_8859_1).contains(fixture.email))

            val store = fixture.createStore()
            val preferences = IturPreferencesDataStore(store, fixture.cipher).preferences.first()
            val rawValue = store.data.first().user_email

            assertEquals(fixture.email, preferences.email)
            assertNotEquals(fixture.email, rawValue)
            assertEquals(fixture.email, fixture.cipher.decrypt(rawValue))
            assertFalse(fixture.file.readText(Charsets.ISO_8859_1).contains(fixture.email))
            assertEquals(256, keySize(fixture.currentKeyAlias))
        } finally {
            fixture.close()
        }
    }

    @Test
    fun legacy128BitCiphertextIsReencryptedAndItsKeyIsRemoved() = runBlocking {
        val fixture = MigrationFixture()
        try {
            val legacyCiphertext = encryptLegacy(fixture.legacyKeyAlias, fixture.email)
            assertEquals(128, keySize(fixture.legacyKeyAlias))
            fixture.writeRaw(IturPreferences(user_email = legacyCiphertext))

            val store = fixture.createStore()
            val preferences = IturPreferencesDataStore(store, fixture.cipher).preferences.first()
            val rawValue = store.data.first().user_email

            assertEquals(fixture.email, preferences.email)
            assertNotEquals(legacyCiphertext, rawValue)
            assertEquals(fixture.email, fixture.cipher.decrypt(rawValue))
            assertFalse(fixture.file.readText(Charsets.ISO_8859_1).contains(fixture.email))
            assertEquals(256, keySize(fixture.currentKeyAlias))
            assertFalse(keyStore().containsAlias(fixture.legacyKeyAlias))
        } finally {
            fixture.close()
        }
    }

    private class MigrationFixture : AutoCloseable {
        private val suffix = UUID.randomUUID().toString()
        val currentKeyAlias = "aoss-1593-current-$suffix"
        val legacyKeyAlias = "aoss-1593-legacy-$suffix"
        val email = "migration@example.com"
        val cipher = AndroidKeystoreEmailCipher(currentKeyAlias, legacyKeyAlias)
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = File(
            InstrumentationRegistry.getInstrumentation().context.cacheDir,
            "aoss-1593-$suffix.pb",
        )

        suspend fun writeRaw(preferences: IturPreferences) {
            file.outputStream().use { IturSettingsSerializer().writeTo(preferences, it) }
        }

        fun createStore() = MultiProcessDataStoreFactory.create(
            serializer = IturSettingsSerializer(),
            migrations = listOf(UserEmailEncryptionMigration(cipher)),
            scope = scope,
        ) { file }

        override fun close() {
            scope.cancel()
            file.delete()
            File("${file.path}.lock").delete()
            keyStore().apply {
                if (containsAlias(currentKeyAlias)) deleteEntry(currentKeyAlias)
                if (containsAlias(legacyKeyAlias)) deleteEntry(legacyKeyAlias)
            }
        }
    }
}

private fun encryptLegacy(keyAlias: String, plainText: String): String {
    val key = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply {
        init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setKeySize(128)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
    }.generateKey()
    val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
    val ciphertext = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
    return Base64.encodeToString(cipher.iv + ciphertext, Base64.NO_WRAP)
}

private fun keySize(keyAlias: String): Int {
    val key = keyStore().getKey(keyAlias, null) as SecretKey
    val keyFactory = SecretKeyFactory.getInstance(key.algorithm, ANDROID_KEYSTORE)
    val keyInfo = keyFactory.getKeySpec(key, KeyInfo::class.java) as KeyInfo
    return keyInfo.keySize
}

private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
