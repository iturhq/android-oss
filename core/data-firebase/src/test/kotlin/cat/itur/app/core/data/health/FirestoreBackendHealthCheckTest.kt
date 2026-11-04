/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.core.data.health

import cat.itur.app.core.data.repository.failedTask
import cat.itur.app.core.data.repository.successfulTask
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GetTokenResult
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FirestoreBackendHealthCheckTest {

    private val auth = mockk<FirebaseAuth>()

    @Test
    fun `Firestore probe declares Firebase Auth as its only prerequisite`() {
        val check = FirestoreBackendHealthCheck(auth, mockk())

        assertEquals(setOf(BackendServiceIds.FIREBASE_AUTH), check.prerequisiteServiceIds)
    }

    @Test
    fun `Firestore probe describes its bounded server diagnostic`() {
        val check = FirestoreBackendHealthCheck(auth, mockk())

        assertEquals(BackendServiceIds.FIREBASE_FIRESTORE, check.service.id)
        assertEquals("Cloud Firestore", check.service.displayName)
        assertEquals(40, check.diagnosticOrder)
        assertEquals("Bounded server read completed", check.successDetail)
    }

    @Test
    fun `Firestore probe refreshes the caller token then reads only that user document from the server`() = runBlocking {
        val user = mockk<FirebaseUser>()
        val reference = mockk<DocumentReference>()
        val firestore = mockk<FirebaseFirestore>()
        every { auth.currentUser } returns user
        every { user.uid } returns "caller-id"
        every { user.getIdToken(true) } returns Tasks.forResult(mockk<GetTokenResult>())
        every { firestore.collection("users").document("caller-id") } returns reference
        every { reference.get(Source.SERVER) } returns successfulTask(mockk())

        FirestoreBackendHealthCheck(auth, firestore).probe()
    }

    @Test
    fun `Firestore recognition accepts nested Firestore failures only`() {
        val check = FirestoreBackendHealthCheck(auth, mockk())
        val firestoreFailure = FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)

        assertTrue(check.recognizes(IllegalStateException("wrapped", firestoreFailure)))
        assertFalse(check.recognizes(IllegalStateException("not firestore")))
    }

    @Test
    fun `Firestore probe propagates failures to the shared health circuit`() = runBlocking {
        val user = mockk<FirebaseUser>()
        val reference = mockk<DocumentReference>()
        val firestore = mockk<FirebaseFirestore>()
        val failure = FirebaseFirestoreException("unavailable", FirebaseFirestoreException.Code.UNAVAILABLE)
        every { auth.currentUser } returns user
        every { user.uid } returns "caller-id"
        every { user.getIdToken(true) } returns Tasks.forResult(mockk<GetTokenResult>())
        every { firestore.collection("users").document("caller-id") } returns reference
        every { reference.get(Source.SERVER) } returns failedTask(failure)

        val propagated = runCatching { FirestoreBackendHealthCheck(auth, firestore).probe() }.exceptionOrNull()

        assertEquals(failure, propagated)
    }
}
