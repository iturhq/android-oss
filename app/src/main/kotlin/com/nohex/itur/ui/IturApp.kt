/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.ui

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import com.nohex.itur.core.ui.IturIcons
import com.nohex.itur.core.ui.components.IturBackground

@Composable
fun IturApp(
    appState: IturAppState,
    modifier: Modifier = Modifier,
) {
    IturBackground(modifier = modifier) {
        val snackbarHostState = remember { SnackbarHostState() }

        IturApp(
            appState = appState,
            snackbarHostState = snackbarHostState,
        )
    }
}

@Composable
internal fun IturApp(
    appState: IturAppState,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
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
                    Icon(IturIcons.Info, contentDescription = "Open source licenses")
                }
            }
        }
    }
}
