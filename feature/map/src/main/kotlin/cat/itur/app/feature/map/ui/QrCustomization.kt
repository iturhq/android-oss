/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map.ui

import androidx.compose.runtime.Composable
import cat.itur.app.core.domain.id.IturActivityId
import cat.itur.app.core.domain.id.url
import cat.itur.app.feature.map.ui.components.qrscan.QRScanSheet

/**
 * The QR display/scan customization points a consumer can override -- bundled into one parameter
 * so [MapScreen] and the internal functions that thread it through don't grow an additional
 * parameter per QR-related hook (see `LongParameterList` in this module's `detekt.yml`).
 *
 * [displayUrl] lets a consumer (e.g. a commercial extension embedding key material in the join
 * URL, CRYP-8F14) customize the URL the organiser's QR code encodes, without this module needing
 * to know why. The default is the plain join URL this module has always encoded.
 */
data class QrCustomization(
    val scanSheet: @Composable (
        onDismissRequest: () -> Unit,
        onScanSuccess: (String) -> Unit,
    ) -> Unit = { onDismissRequest, onScanSuccess ->
        QRScanSheet(
            onDismissRequest = onDismissRequest,
            onScanSuccess = onScanSuccess,
        )
    },
    val displayUrl: (IturActivityId) -> String = { it.url },
)
