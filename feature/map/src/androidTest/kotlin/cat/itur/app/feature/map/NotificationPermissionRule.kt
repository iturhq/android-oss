/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map

import android.Manifest
import android.os.Build
import androidx.test.rule.GrantPermissionRule
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Grants the notification permission where it is a runtime permission.
 *
 * The API 29 CI AVD does not know `POST_NOTIFICATIONS`, so an unconditional
 * [GrantPermissionRule] cannot be shared with Android 13+ devices. Without this conditional rule,
 * [cat.itur.app.feature.map.ui.MapScreen] opens the Android-owned permission dialog after location
 * permission resolves, pausing the test host before Compose assertions can run.
 */
class NotificationPermissionRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
            .apply(base, description)
    } else {
        base
    }
}
