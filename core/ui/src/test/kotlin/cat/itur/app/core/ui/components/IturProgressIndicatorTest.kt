/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.core.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IturProgressIndicatorTest {
    @Test
    fun sweepAnimatesWithoutRotatingTheArcStart() {
        val collapsed = loadingArcGeometry(sweepProgress = 0f)
        val expanded = loadingArcGeometry(sweepProgress = 1f)

        assertEquals(-90f, collapsed.startAngle)
        assertEquals(collapsed.startAngle, expanded.startAngle)
        assertTrue(expanded.sweepAngle > collapsed.sweepAngle)
    }

    @Test
    fun sweepProgressIsClampedToItsAnimationRange() {
        assertEquals(
            loadingArcGeometry(0f),
            loadingArcGeometry(Float.NEGATIVE_INFINITY),
        )
        assertEquals(
            loadingArcGeometry(1f),
            loadingArcGeometry(Float.POSITIVE_INFINITY),
        )
    }
}
