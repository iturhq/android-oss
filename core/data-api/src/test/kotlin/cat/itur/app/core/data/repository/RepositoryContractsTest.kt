/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.core.data.repository

import cat.itur.app.core.domain.id.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class RepositoryContractsTest {

    private val userId = UserId("user-123")

    @Test
    fun `activity filters retain the requested user identity`() {
        assertEquals(userId, ActivityFilter.ByOrganizer(userId).organizerId)
        assertEquals(userId, ActivityFilter.OngoingByOrganizer(userId).organizerId)
        assertEquals(userId, ActivityFilter.OngoingByParticipant(userId).participantId)
    }

    @Test
    fun `data results retain success not found and rejection contracts`() {
        assertEquals("saved", assertIs<DataResult.Success<String>>(DataResult.Success("saved")).data)
        assertEquals("activity-123", assertIs<DataResult.NotFound>(DataResult.NotFound("activity-123")).id)

        val rejection = assertIs<DataResult.Error>(
            DataResult.Error(
                message = "An activity is already active",
                code = DataErrorCode.ACTIVITY_START_LIMIT_REACHED,
            ),
        )

        assertEquals("An activity is already active", rejection.message)
        assertEquals(DataErrorCode.ACTIVITY_START_LIMIT_REACHED, rejection.code)
        assertNull(DataResult.Error("Unspecified rejection").code)
    }

    @Test
    fun `sign in outcomes preserve app owned failure reasons`() {
        assertEquals(
            SignInFailureReason.NO_ACCOUNT,
            assertIs<SignInResult.Failure>(SignInResult.Failure(SignInFailureReason.NO_ACCOUNT)).reason,
        )
        assertIs<SignInResult.Cancelled>(SignInResult.Cancelled)
    }
}
