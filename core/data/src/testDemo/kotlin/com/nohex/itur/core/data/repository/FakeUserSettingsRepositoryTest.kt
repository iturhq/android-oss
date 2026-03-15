/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FakeUserSettingsRepositoryTest {

    private fun repository() = FakeUserSettingsRepository()

    @Test
    fun `GIVEN a new repository WHEN observing settings THEN email is null`() = runBlocking {
        assertNull(repository().userSettings.first().email)
    }

    @Test
    fun `GIVEN a new repository WHEN setting an email THEN settings emits the new email`() = runBlocking {
        val repo = repository()
        repo.setUserEmail("user@example.com")
        assertEquals("user@example.com", repo.userSettings.first().email)
    }

    @Test
    fun `GIVEN an email is set WHEN setting a different email THEN settings emits the latest email`() = runBlocking {
        val repo = repository()
        repo.setUserEmail("first@example.com")
        repo.setUserEmail("second@example.com")
        assertEquals("second@example.com", repo.userSettings.first().email)
    }
}
