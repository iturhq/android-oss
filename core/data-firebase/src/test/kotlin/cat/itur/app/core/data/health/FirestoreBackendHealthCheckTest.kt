/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.core.data.health

import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

class FirestoreBackendHealthCheckTest {

    @Test
    fun `Firestore probe declares Firebase Auth as its only prerequisite`() {
        val check = FirestoreBackendHealthCheck(mockk())

        assertEquals(setOf(BackendServiceIds.FIREBASE_AUTH), check.prerequisiteServiceIds)
    }
}
