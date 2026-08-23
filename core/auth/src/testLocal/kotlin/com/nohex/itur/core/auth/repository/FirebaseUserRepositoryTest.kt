/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.auth.repository

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.nohex.itur.core.auth.config.GoogleSignInConfig
import com.nohex.itur.core.data.health.BackendHealthObservation
import com.nohex.itur.core.data.health.BackendHealthReporter
import com.nohex.itur.core.data.repository.FirestoreCollections
import com.nohex.itur.core.domain.id.UserId
import com.nohex.itur.core.domain.model.User
import dagger.Lazy
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Covers the two [FirebaseUserRepository] branches reachable without a real Android runtime:
 * [FirebaseUserRepository.getCurrentUser]'s Firebase-Auth-backed path, [FirebaseUserRepository.signOut],
 * and [FirebaseUserRepository.getAll] (the only Firestore-touching method here). Deliberately not covered:
 * - `getCurrentUser()`'s anonymous-device-ID fallback, which needs a real Android Keystore via
 *   `MasterKey`/`EncryptedSharedPreferences` -- not reproducible in a plain JVM unit test.
 * - `signIn()`, which drives `CredentialManager`/`GoogleIdTokenCredential` -- Android platform
 *   integration, not Firebase/Firestore behavior.
 * Both would need Robolectric or an instrumented test to exercise meaningfully; out of proportion
 * for Firebase-repository test coverage specifically.
 */
class FirebaseUserRepositoryTest {
    private val firebaseAuth = mockk<FirebaseAuth>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    private val googleSignInConfig = GoogleSignInConfig(webClientId = "test-client-id")
    private val repository = FirebaseUserRepository(
        firebaseAuth,
        context,
        googleSignInConfig,
        Lazy {
            object : BackendHealthReporter {
                override fun report(serviceId: String, observation: BackendHealthObservation) = Unit
            }
        },
    )

    @BeforeTest
    fun setUp() {
        mockkStatic(FirebaseFirestore::class)
    }

    @AfterTest
    fun tearDown() {
        unmockkStatic(FirebaseFirestore::class)
    }

    // --- getCurrentUser ---

    @Test
    fun `GIVEN a signed-in Firebase Auth user WHEN getting the current user THEN returns a mapped RegisteredUser`() = runBlocking {
        val firebaseUser = mockk<FirebaseUser> {
            every { uid } returns "uid-1"
            every { displayName } returns "Jane Doe"
            every { email } returns "jane@example.com"
        }
        every { firebaseAuth.currentUser } returns firebaseUser

        val user = repository.getCurrentUser()

        assertIs<User.RegisteredUser>(user)
        assertEquals(UserId("uid-1"), user.id)
        assertEquals("Jane Doe", user.name)
        assertEquals("jane@example.com", user.email)
    }

    // --- signOut ---

    @Test
    fun `WHEN signing out THEN Firebase Auth's own sign-out is invoked`() = runBlocking {
        repository.signOut()

        verify(exactly = 1) { firebaseAuth.signOut() }
    }

    // --- getAll ---

    @Test
    fun `GIVEN matching user documents WHEN getting all by ID THEN returns mapped RegisteredUsers`() = runBlocking {
        val firestore = mockk<FirebaseFirestore>()
        every { FirebaseFirestore.getInstance() } returns firestore
        val usersCollection = mockk<CollectionReference>()
        every { firestore.collection(FirestoreCollections.USERS) } returns usersCollection
        val query = mockk<Query>()
        every { usersCollection.whereIn(FieldPath.documentId(), listOf("id-1", "id-2")) } returns query

        val doc1 = mockk<DocumentSnapshot> {
            every { id } returns "id-1"
            every { getString("name") } returns "Alice"
            every { getString("email") } returns "alice@example.com"
        }
        val doc2 = mockk<DocumentSnapshot> {
            every { id } returns "id-2"
            every { getString("name") } returns "Bob"
            every { getString("email") } returns null
        }
        val querySnapshot = mockk<QuerySnapshot> { every { documents } returns listOf(doc1, doc2) }
        every { query.get() } returns successfulTask(querySnapshot)

        val result = repository.getAll(listOf(UserId("id-1"), UserId("id-2")))

        assertEquals(2, result.size)
        assertTrue(result.all { it is User.RegisteredUser })
        assertEquals(UserId("id-1"), result[0].id)
        assertEquals("Alice", result[0].name)
        assertEquals("alice@example.com", result[0].email)
        assertEquals(UserId("id-2"), result[1].id)
        assertEquals(null, result[1].email)
    }

    @Test
    fun `GIVEN Firestore throws WHEN getting all users by ID THEN the exception propagates`() = runBlocking {
        val firestore = mockk<FirebaseFirestore>()
        every { FirebaseFirestore.getInstance() } returns firestore
        val usersCollection = mockk<CollectionReference>()
        every { firestore.collection(FirestoreCollections.USERS) } returns usersCollection
        val query = mockk<Query>()
        every { usersCollection.whereIn(FieldPath.documentId(), listOf("id-1")) } returns query
        every { query.get() } returns failedTask(RuntimeException("offline"))

        assertFailsWith<RuntimeException> { repository.getAll(listOf(UserId("id-1"))) }
        Unit
    }
}
