/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.domain.id

/**
 * A unique identifier for a user.
 */
// WARNING: Value class is not well supported by Firestore.
@JvmInline
value class UserId(override val value: String) : DomainId {
    init {
        require(value.isNotBlank()) { "UserId cannot be blank" }
    }

    override fun toString() = value
}
