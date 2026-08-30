/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * A minimal Hilt-enabled host [ComponentActivity] for instrumented Compose tests.
 *
 * `feature:map` is a library module with no launcher `Activity` of its own; `MapScreen()` calls
 * `hiltViewModel()` internally, which needs a Hilt-aware `ViewModelStoreOwner`, so
 * `createAndroidComposeRule` needs an `@AndroidEntryPoint` host in the debug target APK. The host
 * also keeps a physical device awake across the orchestrated suite; otherwise a short device
 * screen timeout can pause later tests even when the run began unlocked.
 */
@AndroidEntryPoint
class HiltTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
