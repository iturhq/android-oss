package com.nohex.itur.core.datastore

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import androidx.datastore.core.MultiProcessDataStoreFactory
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Test-only service hosted in a distinct OS process by the androidTest manifest. */
class RemoteDataStoreService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var release = CompletableDeferred<Unit>()
    private val messenger = Messenger(Handler(Looper.getMainLooper(), ::handleMessage))

    override fun onBind(intent: Intent): IBinder = messenger.binder

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun handleMessage(message: Message): Boolean {
        when (message.what) {
            DataStoreProcessProtocol.START -> startWrite(
                requireNotNull(message.data.getString(DataStoreProcessProtocol.FILE_NAME)),
                requireNotNull(message.replyTo),
            )
            DataStoreProcessProtocol.RELEASE -> release.complete(Unit)
        }
        return true
    }

    private fun startWrite(fileName: String, replies: Messenger) {
        release = CompletableDeferred()
        scope.launch {
            runCatching {
                val dataStore = IturPreferencesDataStore(
                    MultiProcessDataStoreFactory.create(
                        serializer = IturSettingsSerializer(),
                        scope = scope,
                    ) { File(cacheDir, fileName) },
                    TestEmailCipher,
                )
                dataStore.getOrCreateParticipantDisplayName {
                    replies.send(Message.obtain(null, DataStoreProcessProtocol.GENERATOR_ENTERED).apply {
                        arg1 = android.os.Process.myPid()
                    })
                    runBlockingRelease()
                    "Remote generated name"
                }
            }.onSuccess { result ->
                reply(replies, DataStoreProcessProtocol.COMPLETED, result)
            }.onFailure { failure ->
                reply(replies, DataStoreProcessProtocol.FAILED, failure.toString())
            }
        }
    }

    private fun runBlockingRelease() = kotlinx.coroutines.runBlocking { release.await() }

    private fun reply(replies: Messenger, what: Int, result: String) {
        replies.send(Message.obtain(null, what).apply {
            data.putString(DataStoreProcessProtocol.RESULT, result)
        })
    }
}
