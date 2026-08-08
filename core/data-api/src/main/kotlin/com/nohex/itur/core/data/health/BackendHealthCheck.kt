/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.data.health

/**
 * Stable identity and user-facing label for one independently failing backend service.
 */
data class BackendService(
    val id: String,
    val displayName: String,
)

/** Stable service identifiers used when mapping failures to dependent UI actions. */
object BackendServiceIds {
    const val FIREBASE_AUTH = "firebase-auth"
    const val FIREBASE_FIRESTORE = "firebase-firestore"
}

/**
 * Side-effect-free connectivity probe contributed to the application's Hilt set.
 *
 * [probe] returns normally when the service is available and throws when it is not. Callers
 * impose their own timeout, so implementations must also use a bounded native operation.
 * [recognizes] lets an operation failure mark the matching service unavailable immediately,
 * before the next scheduled probe.
 */
interface BackendHealthCheck {
    val service: BackendService

    suspend fun probe()

    fun recognizes(cause: Throwable): Boolean = false
}
