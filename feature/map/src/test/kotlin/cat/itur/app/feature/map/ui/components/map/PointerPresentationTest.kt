/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map.ui.components.map

import cat.itur.app.core.model.ParticipantSignal
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PointerPresentationTest {
    @Test
    fun `each remote role and signal has distinct directional and neutral markers`() {
        listOf(PointerRole.ORGANIZER, PointerRole.OTHER).forEach { role ->
            listOf(null, ParticipantSignal.DELAYED, ParticipantSignal.NEEDS_HELP).forEach { signal ->
                val directional = pointerPresentation(role, signal, directional = true)
                val neutral = pointerPresentation(role, signal, directional = false)

                assertNotEquals(directional.drawable, neutral.drawable)
                assertContains(directional.imageName, role.name.lowercase())
                assertContains(directional.imageName, "directional")
                assertContains(neutral.imageName, role.name.lowercase())
                assertContains(neutral.imageName, "neutral")
            }
        }
    }

    @Test
    fun `every role has distinct okay delayed and needs-help presentations`() {
        PointerRole.entries.forEach { role ->
            val okay = pointerPresentation(role, null, directional = true)
            val delayed = pointerPresentation(role, ParticipantSignal.DELAYED, directional = true)
            val needsHelp = pointerPresentation(role, ParticipantSignal.NEEDS_HELP, directional = true)

            assertEquals("okay", okay.statusLabel)
            assertEquals("delayed", delayed.statusLabel)
            assertEquals("needs-help", needsHelp.statusLabel)
            assertNotEquals(okay.drawable, delayed.drawable)
            assertNotEquals(delayed.drawable, needsHelp.drawable)
            assertContains(delayed.imageName, role.name.lowercase())
            assertContains(needsHelp.imageName, role.name.lowercase())
        }
    }
}
