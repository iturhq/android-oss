/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.data.repository

import android.content.Context
import com.nohex.itur.core.domain.id.UserId
import com.nohex.itur.core.domain.model.User

/**
 * A repository of users.
 */
interface UserRepository {
    /**
     * Retrieves the current user.
     */
    suspend fun getCurrentUser(): User

    /**
     * Retrieves the data for several users, and returns it as a map.
     */
    suspend fun getAll(ids: List<UserId>): List<User>

    /**
     * Signs in via Google and returns the authenticated user.
     */
    suspend fun signIn(context: Context): User

    /**
     * Signs out the current user; subsequent [getCurrentUser] calls return an anonymous user.
     */
    suspend fun signOut()
}
