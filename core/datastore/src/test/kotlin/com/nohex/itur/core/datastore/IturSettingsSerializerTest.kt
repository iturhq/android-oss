/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.datastore

import androidx.datastore.core.CorruptionException
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class IturSettingsSerializerTest {

    private fun serializer() = IturSettingsSerializer()

    private suspend fun IturSettingsSerializer.encode(value: IturPreferences): ByteArray {
        val output = ByteArrayOutputStream()
        writeTo(value, output)
        return output.toByteArray()
    }

    @Test
    fun `GIVEN a serializer WHEN reading defaultValue THEN it has an empty user email`() {
        assertEquals(IturPreferences(user_email = ""), serializer().defaultValue)
    }

    @Test
    fun `GIVEN a populated preferences value WHEN writing then reading it back THEN the round trip is exact`() = runBlocking {
        val original = IturPreferences(user_email = "user@example.com")
        val serializer = serializer()

        val bytes = serializer.encode(original)
        val decoded = serializer.readFrom(ByteArrayInputStream(bytes))

        assertEquals(original, decoded)
    }

    @Test
    fun `GIVEN the default preferences value WHEN writing then reading it back THEN the round trip is exact`() = runBlocking {
        val serializer = serializer()
        val original = serializer.defaultValue

        val bytes = serializer.encode(original)
        val decoded = serializer.readFrom(ByteArrayInputStream(bytes))

        assertEquals(original, decoded)
    }

    @Test
    fun `GIVEN an empty byte stream WHEN reading THEN it decodes to the default value rather than failing`() = runBlocking {
        val serializer = serializer()

        val decoded = serializer.readFrom(ByteArrayInputStream(ByteArray(0)))

        assertEquals(serializer.defaultValue, decoded)
    }

    @Test
    fun `GIVEN a truncated protobuf stream WHEN reading THEN a CorruptionException wraps the decode failure`() = runBlocking {
        val serializer = serializer()
        // A field tag announcing a length-delimited value, with the declared length
        // never actually following: the Wire decoder must run out of bytes mid-message.
        val truncated = byteArrayOf(0x0A, 0x7F)

        val exception = assertFailsWith<CorruptionException> {
            serializer.readFrom(ByteArrayInputStream(truncated))
        }
        assertTrue(exception.cause != null, "the underlying decode failure must not be swallowed")
    }
}
