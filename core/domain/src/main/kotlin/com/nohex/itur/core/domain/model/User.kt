/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.domain.model

import com.nohex.itur.core.domain.id.UserId

/**
 * A user of the system.
 */
sealed class User {
    abstract val id: UserId
    abstract val name: String?
    abstract val email: String?

    /**
     * A user who is not authenticated.
     */
    data class AnonymousUser(
        override val id: UserId,
    ) : User() {
        override val name: String = "You"
        override val email: String? = null
    }

    /**
     * An authenticated user, for whom there is identifiable data.
     */
    data class RegisteredUser(
        override val id: UserId,
        override val name: String?,
        override val email: String?,
    ) : User()
}
