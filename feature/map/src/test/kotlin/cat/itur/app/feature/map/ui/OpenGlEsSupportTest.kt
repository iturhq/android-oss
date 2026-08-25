/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OpenGlEsSupportTest {

    @Test
    fun `OpenGL ES 2 is rejected with a readable version`() {
        val support = openGlEsSupport(0x00020000)

        assertFalse(support.isSupported)
        assertEquals("2.0", support.reportedVersion)
    }

    @Test
    fun `OpenGL ES 3 is accepted`() {
        assertTrue(openGlEsSupport(0x00030000).isSupported)
    }

    @Test
    fun `newer OpenGL ES versions are accepted`() {
        val support = openGlEsSupport(0x00030002)

        assertTrue(support.isSupported)
        assertEquals("3.2", support.reportedVersion)
    }
}
