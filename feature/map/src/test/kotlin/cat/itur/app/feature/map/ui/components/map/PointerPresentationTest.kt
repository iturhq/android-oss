/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map.ui.components.map

import cat.itur.app.core.model.ParticipantSignal
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertNotEquals

class PointerPresentationTest {
    @Test
    fun `each remote role and signal has distinct directional and neutral markers`() {
        PointerRole.entries.forEach { role ->
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
}
