/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.data.health

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.nohex.itur.core.data.repository.FirestoreCollections
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Performs one server-only, one-document read. It never creates, updates, or deletes data.
 */
class FirestoreBackendHealthCheck @Inject constructor(
    private val firestore: FirebaseFirestore,
) : BackendHealthCheck {
    override val service = BackendService(
        id = BackendServiceIds.FIREBASE_FIRESTORE,
        displayName = "Cloud Firestore",
    )

    override suspend fun probe() {
        firestore.collection(FirestoreCollections.ACTIVITIES)
            .limit(1)
            .get(Source.SERVER)
            .await()
    }

    override fun recognizes(cause: Throwable): Boolean = generateSequence(cause) { it.cause }
        .any { it is FirebaseFirestoreException }
}
