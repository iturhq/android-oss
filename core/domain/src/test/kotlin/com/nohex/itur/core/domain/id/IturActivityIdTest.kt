/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.domain.id

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

private const val CORRECT_ACTIVITY_ID = "abcdefghij0123456789"

class IturActivityIdTest {
    @Test
    fun `GIVEN a valid string WHEN creating an activity ID THEN it should be created successfully`() {
        assertIs<IturActivityId>(IturActivityId(CORRECT_ACTIVITY_ID))
    }

    @Test
    fun `GIVEN an invalid string WHEN creating an activity ID THEN it should fail`() {
        assertFailsWith(IllegalArgumentException::class) { IturActivityId("abc123") }
    }

    @Test
    fun `GIVEN a valid ID WHEN creating an activity URL AND extracting the ID back THEN the activity ID should be the same`() {
        val expectedActivityId = IturActivityId(CORRECT_ACTIVITY_ID)
        assertIs<IturActivityId>(expectedActivityId)

        val activityURL = expectedActivityId.url
        val recreatedActivityId = IturActivityId.from(activityURL)

        assertEquals(expectedActivityId, recreatedActivityId)
    }

    @Test
    fun `GIVEN a 19-character string WHEN creating an activity ID THEN it should fail`() {
        assertFailsWith(IllegalArgumentException::class) { IturActivityId("abcdefghij012345678") }
    }

    @Test
    fun `GIVEN a 21-character string WHEN creating an activity ID THEN it should fail`() {
        assertFailsWith(IllegalArgumentException::class) { IturActivityId("abcdefghij01234567890") }
    }

    @Test
    fun `GIVEN a 20-character string with non-alphanumeric characters WHEN creating an activity ID THEN it should fail`() {
        assertFailsWith(IllegalArgumentException::class) { IturActivityId("abcdefghij-123456789") }
    }

    @Test
    fun `GIVEN an unrelated URL WHEN extracting an activity ID THEN it should return null`() {
        assertNull(IturActivityId.from("https://example.com/not-an-activity"))
    }

    @Test
    fun `GIVEN an empty string WHEN extracting an activity ID THEN it should return null`() {
        assertNull(IturActivityId.from(""))
    }

    @Test
    fun `GIVEN a valid activity ID WHEN converting to string THEN it returns the underlying value`() {
        assertEquals(CORRECT_ACTIVITY_ID, IturActivityId(CORRECT_ACTIVITY_ID).toString())
    }
}
