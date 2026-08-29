/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.core.data.repository

/**
 * Firestore root collection names, shared across the Firebase-backed repository implementations
 * (and their tests) so the boundary is defined once rather than repeated as string literals.
 */
object FirestoreCollections {
    const val ACTIVITIES = "activities"
    const val LOCATIONS = "locations"
    const val USERS = "users"
}
