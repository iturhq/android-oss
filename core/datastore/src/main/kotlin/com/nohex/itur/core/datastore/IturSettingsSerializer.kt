/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import okio.buffer
import okio.sink
import okio.source
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

class IturSettingsSerializer @Inject constructor() : Serializer<IturPreferences> {
    override val defaultValue: IturPreferences = IturPreferences()
    override suspend fun readFrom(input: InputStream): IturPreferences {
        try {
            input.source().buffer().use { bufferedSource ->
                return IturPreferences.ADAPTER.decode(bufferedSource)
            }
        } catch (exception: Exception) {
            throw CorruptionException("Cannot decode protobuf (Wire).", exception)
        }
    }

    override suspend fun writeTo(t: IturPreferences, output: OutputStream) {
        output.sink().buffer().use { bufferedSink ->
            IturPreferences.ADAPTER.encode(bufferedSink, t)
            bufferedSink.flush()
        }
    }
}
