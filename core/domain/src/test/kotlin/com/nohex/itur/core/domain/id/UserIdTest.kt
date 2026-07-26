/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.domain.id

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class UserIdTest {

    @Test
    fun `GIVEN a valid string WHEN creating a user ID THEN it should be created successfully`() {
        assertIs<UserId>(UserId("user-123"))
    }

    @Test
    fun `GIVEN an empty string WHEN creating a user ID THEN it should fail`() {
        assertFailsWith(IllegalArgumentException::class) { UserId("") }
    }

    @Test
    fun `GIVEN a whitespace-only string WHEN creating a user ID THEN it should fail`() {
        assertFailsWith(IllegalArgumentException::class) { UserId("   ") }
    }

    @Test
    fun `GIVEN a valid user ID WHEN converting to string THEN it returns the underlying value`() {
        val value = "user-123"
        assertEquals(value, UserId(value).toString())
    }
}
