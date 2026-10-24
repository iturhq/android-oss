/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.ui

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import cat.itur.app.R
import cat.itur.app.core.data.repository.GpxTrackRepository
import cat.itur.app.core.data.repository.InMemoryGpxTrackRepository
import cat.itur.app.core.ui.IturIcons
import cat.itur.app.core.ui.components.IturBackground
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import kotlinx.coroutines.launch

@Composable
fun IturApp(
    appState: IturAppState,
    gpxTrackRepository: GpxTrackRepository = InMemoryGpxTrackRepository(),
    modifier: Modifier = Modifier,
) {
    IturBackground(modifier = modifier) {
        val snackbarHostState = remember { SnackbarHostState() }

        IturApp(
            appState = appState,
            gpxTrackRepository = gpxTrackRepository,
            snackbarHostState = snackbarHostState,
        )
    }
}

@Composable
internal fun IturApp(
    appState: IturAppState,
    gpxTrackRepository: GpxTrackRepository,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val gpxTrack by gpxTrackRepository.track.collectAsState()
    var showClearGpxTrackConfirmation by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    Scaffold(
        modifier =
        modifier
            .semantics {
                testTagsAsResourceId = true
            }
            .systemBarsPadding(),
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(
                snackbarHostState,
                modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal,
                    ),
                ),
        ) {
            Box(
                modifier =
                Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(
                        WindowInsets(0, 0, 0, 0),
                    ),
            ) {
                IturNavHost(
                    appState = appState,
                )

                val context = LocalContext.current
                IconButton(
                    onClick = {
                        context.startActivity(Intent(context, OssLicensesMenuActivity::class.java))
                    },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .testTag("oss_licenses_button"),
                ) {
                    Icon(IturIcons.Info, contentDescription = stringResource(R.string.open_source_licenses))
                }
                if (gpxTrack != null) {
                    IconButton(
                        onClick = { showClearGpxTrackConfirmation = true },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .testTag("clear_gpx_track_button"),
                    ) {
                        Icon(
                            IturIcons.Stop,
                            contentDescription = stringResource(R.string.clear_gpx_track),
                        )
                    }
                }
                if (showClearGpxTrackConfirmation) {
                    AlertDialog(
                        onDismissRequest = { showClearGpxTrackConfirmation = false },
                        title = { Text(stringResource(R.string.clear_gpx_track_confirmation_title)) },
                        text = { Text(stringResource(R.string.clear_gpx_track_confirmation_message)) },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showClearGpxTrackConfirmation = false
                                    coroutineScope.launch { gpxTrackRepository.setTrack(null) }
                                },
                            ) { Text(stringResource(R.string.clear_gpx_track_confirm)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showClearGpxTrackConfirmation = false }) {
                                Text(stringResource(android.R.string.cancel))
                            }
                        },
                    )
                }
            }
        }
    }
}
