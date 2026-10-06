/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.core.data.repository

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cat.itur.app.core.domain.id.UserId
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
class FirebaseActivityRepositoryEmulatorContractTest {
    private val apps = mutableListOf<FirebaseApp>()

    @After
    fun tearDown() {
        apps.forEach(FirebaseApp::delete)
    }

    @Test
    fun trustedActivityLifecycleUsesFirestoreDocumentIdentity() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString(ENABLED_ARGUMENT) == "true")
        val host = arguments.getString(HOST_ARGUMENT) ?: DEFAULT_HOST
        val authPort = arguments.intArgument(AUTH_PORT_ARGUMENT, DEFAULT_AUTH_PORT)
        val firestorePort = arguments.intArgument(FIRESTORE_PORT_ARGUMENT, DEFAULT_FIRESTORE_PORT)
        val functionsPort = arguments.intArgument(FUNCTIONS_PORT_ARGUMENT, DEFAULT_FUNCTIONS_PORT)
        val organizer = adapter("organizer", host, authPort, firestorePort, functionsPort)
        val participant = adapter("participant", host, authPort, firestorePort, functionsPort)

        val created = assertIs<DataResult.Success<cat.itur.app.core.model.IturActivity>>(
            organizer.repository.createActivity(organizer.userId),
        ).data
        val rawDocument = organizer.firestore.collection(FirestoreCollections.ACTIVITIES)
            .document(created.id.value)
            .get()
            .await()
        assertEquals(created.id.value, rawDocument.id)
        assertFalse(rawDocument.data.orEmpty().containsKey("id"))

        val joined = assertIs<DataResult.Success<cat.itur.app.core.model.IturActivity>>(
            participant.repository.addParticipant(created.id, participant.userId),
        ).data
        assertEquals(created.id, joined.id)
        assertEquals(
            created.id,
            assertIs<DataResult.Success<cat.itur.app.core.model.IturActivity>>(
                participant.repository.getActivity(created.id),
            ).data.id,
        )
        assertEquals(
            created.id,
            assertIs<DataResult.Success<cat.itur.app.core.model.IturActivity>>(
                participant.repository.removeParticipant(created.id, participant.userId),
            ).data.id,
        )
    }

    private suspend fun adapter(
        label: String,
        host: String,
        authPort: Int,
        firestorePort: Int,
        functionsPort: Int,
    ): AdapterFixture {
        val app = FirebaseApp.initializeApp(
            ApplicationProvider.getApplicationContext(),
            FirebaseOptions.Builder()
                .setProjectId(PROJECT_ID)
                .setApplicationId("1:1234567890:android:$label")
                .setApiKey("fake-api-key")
                .build(),
            "tier-62fa-$label-${UUID.randomUUID()}",
        )
        apps += app
        val auth = FirebaseAuth.getInstance(app).also { it.useEmulator(host, authPort) }
        val suffix = UUID.randomUUID()
        val user = assertNotNull(
            auth.createUserWithEmailAndPassword("tier-62fa-$label-$suffix@example.test", "correct-horse-battery-staple")
                .await()
                .user,
        )
        val firestore = FirebaseFirestore.getInstance(app).also { it.useEmulator(host, firestorePort) }
        val functions = FirebaseFunctions.getInstance(app).also { it.useEmulator(host, functionsPort) }
        return AdapterFixture(
            userId = UserId(user.uid),
            firestore = firestore,
            repository = FirebaseActivityRepository(firestore, functions),
        )
    }

    private fun android.os.Bundle.intArgument(name: String, default: Int): Int = getString(name)?.toIntOrNull() ?: default

    private data class AdapterFixture(
        val userId: UserId,
        val firestore: FirebaseFirestore,
        val repository: FirebaseActivityRepository,
    )

    private companion object {
        const val PROJECT_ID = "demo-itur"
        const val ENABLED_ARGUMENT = "firebaseEmulatorContract"
        const val HOST_ARGUMENT = "firebaseEmulatorHost"
        const val AUTH_PORT_ARGUMENT = "firebaseAuthPort"
        const val FIRESTORE_PORT_ARGUMENT = "firebaseFirestorePort"
        const val FUNCTIONS_PORT_ARGUMENT = "firebaseFunctionsPort"
        const val DEFAULT_HOST = "10.0.2.2"
        const val DEFAULT_AUTH_PORT = 9099
        const val DEFAULT_FIRESTORE_PORT = 8080
        const val DEFAULT_FUNCTIONS_PORT = 5001
    }
}
