/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.data.repository

import android.content.Context
import com.nohex.itur.core.domain.id.UserId
import com.nohex.itur.core.domain.model.User
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FakeUserRepositoryTest {

    private val context = mockk<Context>(relaxed = true)

    private fun repository() = FakeUserRepository()

    @Test
    fun `GIVEN a new repository WHEN getting current user THEN returns an anonymous user`() {
        runBlocking {
            assertIs<User.AnonymousUser>(repository().getCurrentUser())
        }
    }

    @Test
    fun `GIVEN a signed-out state WHEN signing in THEN returns a registered user`() {
        runBlocking {
            val user = repository().signIn(context)
            assertIs<User.RegisteredUser>(user)
        }
    }

    @Test
    fun `GIVEN a signed-in state WHEN signing out THEN current user is anonymous`() {
        runBlocking {
            val repo = repository()
            repo.signIn(context)
            repo.signOut()
            assertIs<User.AnonymousUser>(repo.getCurrentUser())
        }
    }

    @Test
    fun `GIVEN a list of IDs WHEN getting all THEN returns only users with matching IDs`() = runBlocking {
        val repo = repository()
        val ids = listOf(UserId("1"), UserId("3"))
        val result = repo.getAll(ids)
        assertEquals(2, result.size)
        assertTrue(result.all { it.id in ids })
    }

    @Test
    fun `GIVEN an empty ID list WHEN getting all THEN returns an empty list`() = runBlocking {
        assertTrue(repository().getAll(emptyList()).isEmpty())
    }

    @Test
    fun `GIVEN IDs not in the user list WHEN getting all THEN returns an empty list`() = runBlocking {
        assertTrue(repository().getAll(listOf(UserId("unknown"))).isEmpty())
    }
}
