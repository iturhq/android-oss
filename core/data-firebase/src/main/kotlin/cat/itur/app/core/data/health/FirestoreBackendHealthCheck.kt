/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.core.data.health

import cat.itur.app.core.data.repository.FirestoreCollections
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Performs one server-only, one-document read. It never creates, updates, or deletes data.
 */
class FirestoreBackendHealthCheck @Inject constructor(
    private val firestore: FirebaseFirestore,
) : BackendHealthCheck {
    private companion object {
        const val DIAGNOSTIC_ORDER = 40
    }

    override val service = BackendService(
        id = BackendServiceIds.FIREBASE_FIRESTORE,
        displayName = "Cloud Firestore",
    )
    override val prerequisiteServiceIds = setOf(BackendServiceIds.FIREBASE_AUTH)
    override val diagnosticOrder = DIAGNOSTIC_ORDER
    override val successDetail = "Bounded server read completed"

    override suspend fun probe() {
        firestore.collection(FirestoreCollections.ACTIVITIES)
            .limit(1)
            .get(Source.SERVER)
            .await()
    }

    override fun recognizes(cause: Throwable): Boolean = generateSequence(cause) { it.cause }
        .any { it is FirebaseFirestoreException }
}
