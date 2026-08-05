/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.feature.map

import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * A minimal Hilt-enabled host [ComponentActivity] for instrumented Compose tests.
 *
 * `feature:map` is a library module with no launcher `Activity` of its own; `MapScreen()` calls
 * `hiltViewModel()` internally, which needs a Hilt-aware `ViewModelStoreOwner`, so
 * `createAndroidComposeRule` needs an `@AndroidEntryPoint` host in the debug target APK.
 */
@AndroidEntryPoint
class HiltTestActivity : ComponentActivity()
