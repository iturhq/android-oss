/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.core.datastore

/**
 * Encrypts/decrypts a single sensitive string value before it is persisted to
 * [IturPreferences] by [IturPreferencesDataStore]. An empty string round-trips as itself
 * (there is nothing to protect, and it is [IturPreferences]'s default value).
 */
interface EmailCipher {
    fun encrypt(plainText: String): String
    fun decrypt(storedValue: String): String
}
