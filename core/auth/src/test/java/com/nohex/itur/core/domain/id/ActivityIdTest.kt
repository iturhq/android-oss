/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.domain.id

import org.junit.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class ActivityIdTest {

    @Test
    fun `GIVEN a valid string WHEN creating an activity ID THEN it should be created successfully`() {
        assertIs<IturActivityId>(IturActivityId("abcdefghij0987654321"))
    }

    @Test
    fun `GIVEN an invalid string WHEN creating an activity ID THEN it should fail`() {
        assertFailsWith(IllegalArgumentException::class) { IturActivityId("activity-123") }
    }
}
