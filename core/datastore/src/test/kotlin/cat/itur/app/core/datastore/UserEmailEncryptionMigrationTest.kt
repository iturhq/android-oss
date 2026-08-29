/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.core.datastore

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakeStoredEmailMigration : StoredEmailMigration {
    var cleanedUp = false

    override fun shouldMigrate(storedValue: String): Boolean {
        val usesLegacyFormat = storedValue.isNotEmpty() && !storedValue.startsWith("enc:")
        return usesLegacyFormat
    }

    override fun migrate(storedValue: String): String {
        val alreadyEncrypted = storedValue.startsWith("enc:")
        return if (alreadyEncrypted) storedValue else "enc:$storedValue"
    }

    override fun cleanUp() {
        cleanedUp = true
    }
}

class UserEmailEncryptionMigrationTest {

    @Test
    fun `GIVEN a legacy plaintext email WHEN migrating THEN only ciphertext remains`() = runBlocking {
        val formatMigration = FakeStoredEmailMigration()
        val migration = UserEmailEncryptionMigration(formatMigration)
        val plaintext = IturPreferences(user_email = "person@example.com")

        assertTrue(migration.shouldMigrate(plaintext))

        val migrated = migration.migrate(plaintext)
        migration.cleanUp()

        assertEquals("enc:person@example.com", migrated.user_email)
        assertTrue(formatMigration.cleanedUp)
    }

    @Test
    fun `GIVEN current ciphertext WHEN checking migration THEN it is left unchanged`() = runBlocking {
        val migration = UserEmailEncryptionMigration(FakeStoredEmailMigration())
        val encrypted = IturPreferences(user_email = "enc:ciphertext")

        assertFalse(migration.shouldMigrate(encrypted))
        assertEquals(encrypted, migration.migrate(encrypted))
    }
}
