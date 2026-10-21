/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import cat.itur.app.core.data.repository.GpxImportResult
import cat.itur.app.core.data.repository.GpxTrackRepository
import cat.itur.app.core.data.repository.importFrom
import cat.itur.app.core.ui.GeneratedPreview
import cat.itur.app.core.ui.theme.IturTheme
import cat.itur.app.ui.IturApp
import cat.itur.app.ui.IturAppState
import cat.itur.app.ui.rememberIturAppState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var gpxTrackRepository: GpxTrackRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            splashScreen.setKeepOnScreenCondition { false }

            val appState = rememberIturAppState()
            CompositionLocalProvider {
                IturTheme {
                    IturApp(appState)
                }
            }
        }
        importGpx(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        importGpx(intent)
    }

    private fun importGpx(intent: Intent) = lifecycleScope.launch {
        when (gpxTrackRepository.importFrom(intent, contentResolver)) {
            GpxImportResult.Imported -> Toast.makeText(this@MainActivity, "GPX track loaded", Toast.LENGTH_SHORT).show()
            GpxImportResult.Rejected -> Toast.makeText(this@MainActivity, "Couldn't load GPX track", Toast.LENGTH_SHORT).show()
            GpxImportResult.Ignored -> Unit
        }
    }
}

@GeneratedPreview
@Preview(showBackground = true)
@Composable
fun MainActivityPreview() {
    val appState = rememberIturAppState(rememberNavController())
    MainActivityPreviewContent(appState)
}

@GeneratedPreview
@Composable
private fun MainActivityPreviewContent(appState: IturAppState) = IturApp(appState)
