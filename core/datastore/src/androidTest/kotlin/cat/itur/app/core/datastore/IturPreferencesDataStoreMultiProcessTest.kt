/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.core.datastore

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import androidx.datastore.core.MultiProcessDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Proves one atomic winner while two OS processes update the same physical DataStore file. */
@RunWith(AndroidJUnit4::class)
class IturPreferencesDataStoreMultiProcessTest {

    @Test
    fun concurrentProcessesPersistAtMostOneParticipantNameWinner() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().context
        val fileName = "svcs-374a-${UUID.randomUUID()}.pb"
        val remote = RemoteProcessClient(context)
        val localScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            remote.bind()
            remote.start(fileName)
            remote.generatorEntered.await()

            val localStore = IturPreferencesDataStore(
                createStore(File(context.cacheDir, fileName), localScope),
                TestEmailCipher,
            )
            val localResult = async(Dispatchers.IO) {
                localStore.getOrCreateParticipantDisplayName { "Local generated name" }
            }
            assertFalse(localResult.isCompleted)

            remote.release()
            val remoteResult = withTimeout(20_000) { remote.completed.await() }
            val localWinner = withTimeout(20_000) { localResult.await() }

            assertEquals("Remote generated name", remoteResult)
            assertEquals(remoteResult, localWinner)
            assertEquals(remoteResult, localStore.preferences.first().participantDisplayName)
            assertTrue(remote.remotePid != android.os.Process.myPid())
        } finally {
            localScope.cancel()
            remote.close()
        }
    }

    private fun createStore(file: File, scope: CoroutineScope) = MultiProcessDataStoreFactory.create(
        serializer = IturSettingsSerializer(),
        scope = scope,
    ) { file }
}

private class RemoteProcessClient(private val context: Context) : AutoCloseable {
    val generatorEntered = CompletableDeferred<Unit>()
    val completed = CompletableDeferred<String>()
    var remotePid: Int = android.os.Process.myPid()
        private set

    private val connected = CompletableDeferred<Messenger>()
    private val replies = Messenger(
        Handler(Looper.getMainLooper()) { message ->
            when (message.what) {
                DataStoreProcessProtocol.GENERATOR_ENTERED -> {
                    remotePid = message.arg1
                    generatorEntered.complete(Unit)
                }
                DataStoreProcessProtocol.COMPLETED -> completed.complete(
                    requireNotNull(message.data.getString(DataStoreProcessProtocol.RESULT)),
                )
                DataStoreProcessProtocol.FAILED -> completed.completeExceptionally(
                    AssertionError(message.data.getString(DataStoreProcessProtocol.RESULT)),
                )
            }
            true
        },
    )
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            connected.complete(Messenger(service))
        }

        override fun onServiceDisconnected(name: ComponentName) = Unit
    }

    suspend fun bind() {
        val intent = Intent(context, RemoteDataStoreService::class.java)
        check(context.bindService(intent, connection, Context.BIND_AUTO_CREATE))
        withTimeout(10_000) { connected.await() }
    }

    suspend fun start(fileName: String) {
        send(DataStoreProcessProtocol.START) {
            data.putString(DataStoreProcessProtocol.FILE_NAME, fileName)
            replyTo = replies
        }
    }

    suspend fun release() = send(DataStoreProcessProtocol.RELEASE)

    private suspend fun send(what: Int, configure: Message.() -> Unit = {}) {
        connected.await().send(Message.obtain(null, what).apply(configure))
    }

    override fun close() {
        if (connected.isCompleted) context.unbindService(connection)
    }
}

internal object DataStoreProcessProtocol {
    const val START = 1
    const val RELEASE = 2
    const val GENERATOR_ENTERED = 3
    const val COMPLETED = 4
    const val FAILED = 5
    const val FILE_NAME = "file-name"
    const val RESULT = "result"
}

internal object TestEmailCipher : EmailCipher {
    override fun encrypt(plainText: String): String = plainText
    override fun decrypt(storedValue: String): String = storedValue
}
