/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Wakes the device and dismisses its keyguard before the test body runs.
 *
 * An emulator's default AVD image boots with no secure lock screen and never sleeps during an
 * automated run, so `ActivityScenario`/`createAndroidComposeRule` reliably reach `RESUMED`. A
 * physical device lab does not: if the screen has timed out or the device was left locked, the
 * host `Activity` -- and everything it hosts, including `MapView`'s `getMapAsync`/style-loaded
 * callback -- stays `STOPPED` behind the keyguard indefinitely, independent of anything the test
 * itself does. Apply this rule before any rule that launches an `Activity`.
 */
class WakeDeviceRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            if (!device.isScreenOn) {
                device.wakeUp()
            }
            device.executeShellCommand("wm dismiss-keyguard")
            base.evaluate()
        }
    }
}
