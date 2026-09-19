/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.core.datastore

import java.security.ProviderException
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val CURRENT_PREFIX = "aes256-gcm:v2:"
private const val TRANSFORMATION = "AES/GCM/NoPadding"

class AndroidKeystoreEmailCipherTest {

    @Test
    fun `GIVEN an email WHEN encrypted THEN current ciphertext decrypts with the current key`() {
        val cipher = cipher()

        val encrypted = cipher.encrypt("person@example.com")

        assertTrue(encrypted.startsWith(CURRENT_PREFIX))
        assertEquals("person@example.com", cipher.decrypt(encrypted))
        assertEquals("", cipher.encrypt(""))
    }

    @Test
    fun `GIVEN malformed or foreign data WHEN decrypting THEN email is recovered as unset`() {
        val logger = RecordingLogger()
        val cipher = cipher(logger = logger)
        val encrypted = cipher.encrypt("person@example.com")

        assertEquals("", cipher.decrypt("person@example.com"))
        assertEquals("", cipher.decrypt("$CURRENT_PREFIX.not-base64"))
        assertEquals("", cipher.decrypt("$CURRENT_PREFIX${Base64.getEncoder().encodeToString(ByteArray(13))}"))
        assertEquals("", cipher.decrypt(encrypted.dropLast(2) + "xx"))

        assertEquals(4, logger.warnings.size)
    }

    @Test
    fun `GIVEN no stored email WHEN decrypting THEN it remains unset without a warning`() {
        val logger = RecordingLogger()

        assertEquals("", cipher(logger = logger).decrypt(""))
        assertEquals(emptyList(), logger.warnings)
    }

    @Test
    fun `GIVEN a shortened current ciphertext WHEN decrypting THEN email is recovered as unset`() {
        val logger = RecordingLogger()
        val cipher = cipher(logger = logger)
        val encodedIvOnly = Base64.getEncoder().encodeToString(ByteArray(12))

        assertEquals("", cipher.decrypt("${CURRENT_PREFIX}$encodedIvOnly"))
        assertEquals(1, logger.warnings.size)
    }

    @Test
    fun `GIVEN plaintext legacy email WHEN migrated THEN it is reencrypted`() {
        val cipher = cipher()

        val migrated = cipher.migrate("person@example.com")

        assertTrue(cipher.shouldMigrate("person@example.com"))
        assertEquals("person@example.com", cipher.decrypt(migrated))
    }

    @Test
    fun `GIVEN legacy ciphertext WHEN migrated THEN it is reencrypted with current key`() {
        val keys = FakeEmailKeyStore(legacy = aesKey())
        val cipher = cipher(keys)
        val legacyCiphertext = encryptWith(keys.legacyKey()!!, "person@example.com")

        val migrated = cipher.migrate(legacyCiphertext)

        assertEquals("person@example.com", cipher.decrypt(migrated))
        assertTrue(cipher.shouldMigrate(legacyCiphertext))
    }

    @Test
    fun `GIVEN legacy ciphertext from another key WHEN migrated THEN it is removed`() {
        val logger = RecordingLogger()
        val keys = FakeEmailKeyStore(legacy = aesKey())
        val cipher = cipher(keys, logger)
        val foreignCiphertext = encryptWith(aesKey(), "person@example.com")

        assertEquals("", cipher.migrate(foreignCiphertext))
        assertEquals(2, logger.warnings.size)
    }

    @Test
    fun `GIVEN unreadable legacy data WHEN migrated THEN it is removed and cleanup deletes legacy key`() {
        val logger = RecordingLogger()
        val keys = FakeEmailKeyStore(legacy = aesKey())
        val cipher = cipher(keys, logger)

        assertEquals("", cipher.migrate("not-base64"))
        cipher.cleanUp()

        assertTrue(logger.warnings.isNotEmpty())
        assertEquals(1, logger.debugs.size)
        assertTrue(keys.deletedLegacyKey)
    }

    @Test
    fun `GIVEN current data or no legacy key WHEN checking migration THEN it is unchanged`() {
        val cipher = cipher()
        val current = cipher.encrypt("person@example.com")

        assertFalse(cipher.shouldMigrate(""))
        assertFalse(cipher.shouldMigrate(current))
        assertEquals("", cipher.migrate(""))
        assertEquals(current, cipher.migrate(current))
    }

    @Test
    fun `GIVEN the current keystore reports a provider failure WHEN decrypting THEN email is recovered as unset`() {
        val logger = RecordingLogger()
        val cipher = cipher(
            keys = FakeEmailKeyStore(currentKeyFailure = ProviderException("keystore unavailable")),
            logger = logger,
        )

        assertEquals("", cipher.decrypt("${CURRENT_PREFIX}payload"))
        assertEquals(1, logger.warnings.size)
    }

    @Test
    fun `GIVEN a legacy alias without an accessible key WHEN migrating plaintext THEN it is reencrypted`() {
        val keys = FakeEmailKeyStore(legacyPresentWithoutKey = true)
        val cipher = cipher(keys)

        val migrated = cipher.migrate("person@example.com")

        assertTrue(cipher.shouldMigrate(cipher.encrypt("current@example.com")))
        assertEquals("person@example.com", cipher.decrypt(migrated))
    }

    @Test
    fun `GIVEN a legacy provider failure WHEN migrating unreadable data THEN it is removed`() {
        val logger = RecordingLogger()
        val cipher = cipher(
            keys = FakeEmailKeyStore(legacy = aesKey()),
            logger = logger,
            encoding = ProviderFailingDecodingEncoding,
        )

        assertEquals("", cipher.migrate("not-a-legacy-email"))
        assertEquals(2, logger.warnings.size)
    }

    private fun cipher(
        keys: FakeEmailKeyStore = FakeEmailKeyStore(),
        logger: RecordingLogger = RecordingLogger(),
        encoding: EmailCipherEncoding = JvmEmailCipherEncoding,
    ) = AndroidKeystoreEmailCipher(keys, logger, encoding)

    private fun aesKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private fun encryptWith(key: SecretKey, plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
        return Base64.getEncoder().encodeToString(cipher.iv + cipher.doFinal(plainText.toByteArray()))
    }
}

private class FakeEmailKeyStore(
    private val current: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey(),
    private var legacy: SecretKey? = null,
    private val currentKeyFailure: RuntimeException? = null,
    private val legacyPresentWithoutKey: Boolean = false,
) : EmailKeyStore {
    var deletedLegacyKey = false

    override fun currentKey(): SecretKey = currentKeyFailure?.let { throw it } ?: current
    override fun hasLegacyKey(): Boolean = legacy != null || legacyPresentWithoutKey
    override fun legacyKey(): SecretKey? = legacy
    override fun deleteLegacyKey() {
        deletedLegacyKey = true
        legacy = null
    }
}

private class RecordingLogger : EmailCipherLogger {
    val warnings = mutableListOf<String>()
    val debugs = mutableListOf<String>()

    override fun warning(message: String, exception: Exception?) {
        warnings += message
    }

    override fun debug(message: String, exception: Exception) {
        debugs += message
    }
}

private object JvmEmailCipherEncoding : EmailCipherEncoding {
    override fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    override fun decode(value: String): ByteArray = Base64.getDecoder().decode(value)
}

private object ProviderFailingDecodingEncoding : EmailCipherEncoding {
    override fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    override fun decode(value: String): ByteArray = throw ProviderException("invalid provider payload")
}
