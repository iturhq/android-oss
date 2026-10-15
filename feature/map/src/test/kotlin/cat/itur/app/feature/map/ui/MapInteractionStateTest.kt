/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map.ui

import androidx.compose.runtime.mutableStateOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MapInteractionStateTest {
    private fun state() = MapInteractionState(
        locationPermissionState = mutableStateOf(true),
        centeredOnInitialLocationState = mutableStateOf(false),
    )

    @Test
    fun `stop confirmation can be cancelled without invoking the action`() {
        val state = state()
        var invocationCount = 0

        state.requestStopActivityConfirmation()
        state.cancelStopActivityConfirmation()
        state.confirmStopActivity { invocationCount += 1 }

        assertFalse(state.showStopActivityConfirmation)
        assertEquals(0, invocationCount)
    }

    @Test
    fun `stop confirmation consumes the request and invokes the action only once`() {
        val state = state()
        var invocationCount = 0

        state.requestStopActivityConfirmation()
        assertTrue(state.showStopActivityConfirmation)
        state.confirmStopActivity { invocationCount += 1 }
        state.confirmStopActivity { invocationCount += 1 }

        assertFalse(state.showStopActivityConfirmation)
        assertEquals(1, invocationCount)
    }
}
