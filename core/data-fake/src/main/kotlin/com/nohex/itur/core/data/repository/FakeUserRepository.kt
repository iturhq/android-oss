/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.data.repository

import android.content.Context
import com.nohex.itur.core.domain.id.UserId
import com.nohex.itur.core.domain.model.User

class FakeUserRepository : UserRepository {
    val users = listOf(
        User.AnonymousUser(UserId("1")),
        User.RegisteredUser(
            id = UserId("2"),
            name = "William Henry Harrisson",
            email = "william.henry.harrison@example.com",
        ),
        User.AnonymousUser(UserId("3")),
        User.RegisteredUser(
            id = UserId("4"),
            name = "James Iredell",
            email = "james.iredell@example.com",
        ),
        User.AnonymousUser(UserId("5")),
        User.RegisteredUser(
            id = UserId("6"),
            name = "Jame A. Garfield",
            email = "james.a.garfield@example.com",
        ),
    )

    private val anonymousUser = users.filterIsInstance<User.AnonymousUser>().first()
    private val registeredUser = users.filterIsInstance<User.RegisteredUser>().first()
    private var currentUser: User = anonymousUser

    override suspend fun getCurrentUser(): User = currentUser

    override suspend fun getAll(ids: List<UserId>): List<User> = users.filter { it.id in ids }

    override suspend fun signIn(context: Context): User {
        currentUser = registeredUser
        return currentUser
    }

    override suspend fun signOut() {
        currentUser = anonymousUser
    }
}
