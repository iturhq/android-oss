/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.datastore

import androidx.datastore.core.MultiProcessDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Uses independently constructed multi-process stores over one physical file. This covers the
 * storage-factory guarantee which an in-memory [androidx.datastore.core.DataStore] fake cannot:
 * the transaction that chooses a first participant name is coherent across store instances.
 */
@RunWith(AndroidJUnit4::class)
class IturPreferencesDataStoreMultiProcessTest {

    @Test
    fun concurrentFactoriesPersistAtMostOneParticipantNameWinner() = runBlocking {
        val file = testFile()
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val first = IturPreferencesDataStore(createStore(file, firstScope), TestEmailCipher)
            val second = IturPreferencesDataStore(createStore(file, secondScope), TestEmailCipher)
            val firstGeneratorEntered = CountDownLatch(1)
            val releaseFirstGenerator = CountDownLatch(1)

            val firstResult = async(Dispatchers.IO) {
                first.getOrCreateParticipantDisplayName {
                    firstGeneratorEntered.countDown()
                    check(releaseFirstGenerator.await(10, TimeUnit.SECONDS)) {
                        "Test did not release the first name generator"
                    }
                    "First generated name"
                }
            }
            assertTrue(firstGeneratorEntered.await(10, TimeUnit.SECONDS))

            val secondStarted = CountDownLatch(1)
            val secondResult = async(Dispatchers.IO) {
                secondStarted.countDown()
                second.getOrCreateParticipantDisplayName { "Second generated name" }
            }
            assertTrue(secondStarted.await(10, TimeUnit.SECONDS))
            // The second factory has entered the competing call but cannot publish another winner
            // until the first transaction commits. This makes the overlap deterministic.
            assertFalse(secondResult.isCompleted)

            releaseFirstGenerator.countDown()
            val results = withTimeout(20_000) { awaitAll(firstResult, secondResult) }

            assertEquals(1, results.distinct().size)
            assertEquals("First generated name", results.single())
            assertEquals(results.single(), first.preferences.first().participantDisplayName)
            assertEquals(results.single(), second.preferences.first().participantDisplayName)
        } finally {
            firstScope.cancel()
            secondScope.cancel()
        }
    }

    private fun createStore(
        file: File,
        scope: CoroutineScope,
    ) = MultiProcessDataStoreFactory.create(
        serializer = IturSettingsSerializer(),
        scope = scope,
    ) { file }

    private fun testFile(): File = File(
        InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
        "svcs-374a-${UUID.randomUUID()}.pb",
    )

    private object TestEmailCipher : EmailCipher {
        override fun encrypt(plainText: String): String = plainText

        override fun decrypt(storedValue: String): String = storedValue
    }
}
