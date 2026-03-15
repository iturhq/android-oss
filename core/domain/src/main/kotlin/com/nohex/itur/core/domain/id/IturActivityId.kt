/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.domain.id

const val ACTIVITY_URL_PREFIX: String = "https://itur.cat/activity/"
private val ACTIVITY_URL_REGEX = Regex("""${ACTIVITY_URL_PREFIX}([A-Za-z0-9]{20})""")

// WARNING: Value class is not well supported by Firestore.
@JvmInline
value class IturActivityId(override val value: String) : DomainId {
    init {
        require(value.length == 20 && value.all { it.isLetterOrDigit() }) {
            "Invalid activity identifier: $value"
        }
    }

    override fun toString() = value

    companion object {
        /**
         * Extract the activity identifier from an activity URL.
         */
        fun from(url: String): IturActivityId? = ACTIVITY_URL_REGEX.find(url)?.groupValues?.get(1)?.let { IturActivityId(it) }
    }
}

/**
 * Provide a URL for this activity identifier.
 */
val IturActivityId.url: String
    get() = "${ACTIVITY_URL_PREFIX}${this.value}"
