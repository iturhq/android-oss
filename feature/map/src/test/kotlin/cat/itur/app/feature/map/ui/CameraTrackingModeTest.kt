/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map.ui

import androidx.compose.runtime.mutableStateOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CameraTrackingModeTest {

    @Test
    fun `requesting a mode enables it and replaces the other active mode`() {
        assertEquals(CameraTrackingMode.USER, CameraTrackingMode.NONE.toggle(CameraTrackingMode.USER))
        assertEquals(CameraTrackingMode.GROUP, CameraTrackingMode.USER.toggle(CameraTrackingMode.GROUP))
        assertEquals(CameraTrackingMode.USER, CameraTrackingMode.GROUP.toggle(CameraTrackingMode.USER))
    }

    @Test
    fun `requesting the active mode disables tracking`() {
        assertEquals(CameraTrackingMode.NONE, CameraTrackingMode.USER.toggle(CameraTrackingMode.USER))
        assertEquals(CameraTrackingMode.NONE, CameraTrackingMode.GROUP.toggle(CameraTrackingMode.GROUP))
    }

    @Test
    fun `manual zoom always disables active tracking`() {
        assertEquals(CameraTrackingMode.NONE, CameraTrackingMode.USER.stopForManualZoom())
        assertEquals(CameraTrackingMode.NONE, CameraTrackingMode.GROUP.stopForManualZoom())
    }

    @Test
    fun `interaction state replaces tracking and preserves a manual zoom override`() {
        val interaction = MapInteractionState(
            locationPermissionState = mutableStateOf(false),
            centeredOnInitialLocationState = mutableStateOf(false),
        )

        interaction.toggleCameraTracking(CameraTrackingMode.USER)
        assertEquals(CameraTrackingMode.USER, interaction.cameraTrackingMode)
        assertEquals(false, interaction.hasManualZoomOverride)

        interaction.toggleCameraTracking(CameraTrackingMode.GROUP)
        assertEquals(CameraTrackingMode.GROUP, interaction.cameraTrackingMode)

        interaction.stopCameraTrackingForManualZoom()
        assertEquals(CameraTrackingMode.NONE, interaction.cameraTrackingMode)
        assertEquals(true, interaction.hasManualZoomOverride)
    }

    @Test
    fun `none cannot be requested as a tracking mode`() {
        assertFailsWith<IllegalArgumentException> {
            CameraTrackingMode.NONE.toggle(CameraTrackingMode.NONE)
        }
    }
}
