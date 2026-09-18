/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.core.data.health

import cat.itur.app.core.data.repository.FirestoreCollections
import cat.itur.app.core.data.repository.failedTask
import cat.itur.app.core.data.repository.successfulTask
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FirestoreBackendHealthCheckTest {

    @Test
    fun `Firestore probe declares Firebase Auth as its only prerequisite`() {
        val check = FirestoreBackendHealthCheck(mockk())

        assertEquals(setOf(BackendServiceIds.FIREBASE_AUTH), check.prerequisiteServiceIds)
    }

    @Test
    fun `Firestore probe describes its bounded server diagnostic`() {
        val check = FirestoreBackendHealthCheck(mockk())

        assertEquals(BackendServiceIds.FIREBASE_FIRESTORE, check.service.id)
        assertEquals("Cloud Firestore", check.service.displayName)
        assertEquals(40, check.diagnosticOrder)
        assertEquals("Bounded server read completed", check.successDetail)
    }

    @Test
    fun `Firestore probe performs one server-only bounded read`() = runBlocking {
        val collection = mockk<CollectionReference>()
        val query = mockk<Query>()
        val firestore = mockk<FirebaseFirestore>()
        every { firestore.collection(FirestoreCollections.ACTIVITIES) } returns collection
        every { collection.limit(1) } returns query
        every { query.get(Source.SERVER) } returns successfulTask(mockk())

        FirestoreBackendHealthCheck(firestore).probe()
    }

    @Test
    fun `Firestore recognition accepts nested Firestore failures only`() {
        val check = FirestoreBackendHealthCheck(mockk())
        val firestoreFailure = FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)

        assertTrue(check.recognizes(IllegalStateException("wrapped", firestoreFailure)))
        assertFalse(check.recognizes(IllegalStateException("not firestore")))
    }

    @Test
    fun `Firestore probe propagates failures to the shared health circuit`() = runBlocking {
        val collection = mockk<CollectionReference>()
        val query = mockk<Query>()
        val firestore = mockk<FirebaseFirestore>()
        val failure = FirebaseFirestoreException("unavailable", FirebaseFirestoreException.Code.UNAVAILABLE)
        every { firestore.collection(FirestoreCollections.ACTIVITIES) } returns collection
        every { collection.limit(1) } returns query
        every { query.get(Source.SERVER) } returns failedTask(failure)

        val propagated = runCatching { FirestoreBackendHealthCheck(firestore).probe() }.exceptionOrNull()

        assertEquals(failure, propagated)
    }
}
