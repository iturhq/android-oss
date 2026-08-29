/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.core.datastore

import androidx.datastore.core.DataMigration

/** Migration operations owned by the stored ciphertext format rather than DataStore. */
internal interface StoredEmailMigration {
    fun shouldMigrate(storedValue: String): Boolean
    fun migrate(storedValue: String): String
    fun cleanUp()
}

/** Encrypts legacy values transactionally before DataStore exposes its first value. */
internal class UserEmailEncryptionMigration(
    private val storedEmailMigration: StoredEmailMigration,
) : DataMigration<IturPreferences> {
    override suspend fun shouldMigrate(currentData: IturPreferences): Boolean {
        val storedValue = currentData.user_email
        return storedEmailMigration.shouldMigrate(storedValue)
    }

    override suspend fun migrate(currentData: IturPreferences): IturPreferences = currentData.copy(
        user_email = storedEmailMigration.migrate(currentData.user_email),
    )

    override suspend fun cleanUp() = storedEmailMigration.cleanUp()
}
