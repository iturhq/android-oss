/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.feature.map.ui.components.qrdisplay

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.URISyntaxException

/**
 * ZXing's own quiet-zone margin, in QR modules (its standards-recommended minimum), kept
 * independent of any caller-requested pixel [padding][rememberQrBitmapPainter] -- see
 * [buildQRBitmap].
 */
private const val QUIET_ZONE_MODULES = 4

@Composable
fun QRImage(qrURL: String, size: Dp = 300.dp) {
    Image(
        painter = rememberQrBitmapPainter(qrURL),
        contentDescription = qrURL,
        modifier = Modifier.size(size),
    )
}

@Composable
fun rememberQrBitmapPainter(
    qrData: String,
    size: Dp = 300.dp,
    padding: Dp = 0.dp,
): BitmapPainter {
    val density = LocalDensity.current
    // Calculate the right amount of pixels for the density.
    val sizePx = with(density) { size.roundToPx() }
    val paddingPx = with(density) { padding.roundToPx() }

    val qrBitmapState = remember {
        mutableStateOf<Bitmap?>(null)
    }

    LaunchedEffect(qrData) {
        val qrBitmap = buildQRBitmap(qrData, sizePx, paddingPx)
        qrBitmapState.value = qrBitmap
    }

    val bitmap = qrBitmapState.value ?: buildBlankBitmap(sizePx)

    return remember(bitmap) {
        BitmapPainter(bitmap.asImageBitmap())
    }
}

/**
 * Builds a blank bitmap to symbolise a missing QR.
 */
private fun buildBlankBitmap(sizePx: Int): Bitmap {
    // Only alpha info is stored. For a transparent image, that's enough.
    return createBitmap(sizePx, sizePx, Bitmap.Config.ALPHA_8).apply {
        eraseColor(Color.TRANSPARENT)
    }
}

/**
 * Builds a bitmap representing the QR code for the given data.
 * Suspendable so that it can be used in a coroutine.
 */
private suspend fun buildQRBitmap(
    qrData: String,
    sizePx: Int,
    paddingPx: Int,
): Bitmap? = withContext(Dispatchers.IO) {
    if (!qrData.isValidQrUrl()) return@withContext null

    val qrCodeWriter = QRCodeWriter()

    // ZXing's own MARGIN hint is a count of QR modules, not pixels, so it can't express the
    // caller's Dp-based padding directly -- doing so would make the border's real size vary with
    // screen density (the same padding: Dp would become a larger module-margin at higher
    // densities, since paddingPx scales with density but a "module" doesn't). Leave it at the
    // spec's standard quiet zone regardless of paddingPx, and add the requested pixel padding as
    // a real pixel border ourselves in renderQrPixels.
    val encodeHints = mapOf(EncodeHintType.MARGIN to QUIET_ZONE_MODULES)
    val requestedContentSizePx = (sizePx - 2 * paddingPx).coerceAtLeast(1)

    try {
        val bitmapMatrix = qrCodeWriter.encode(
            qrData,
            BarcodeFormat.QR_CODE,
            requestedContentSizePx,
            requestedContentSizePx,
            encodeHints,
        )

        val (finalSize, pixels) = renderQrPixels(bitmapMatrix, paddingPx)
        Bitmap.createBitmap(pixels, finalSize, finalSize, Bitmap.Config.ARGB_8888)
    } catch (_: WriterException) {
        null
    }
}

/**
 * Renders [bitmapMatrix]'s modules into a same-aspect ARGB pixel array, [paddingPx] pixels larger
 * on each side than the matrix itself, that border filled with [Color.WHITE]. Kept free of
 * [Bitmap] entirely (a real Android/Robolectric runtime, unlike ZXing's own encoder, would be
 * needed to construct one) so it can be unit tested directly against a real encoded [BitMatrix].
 *
 * Uses the matrix's own (encoder-assigned) dimensions rather than the originally requested size,
 * since ZXing rounds up to the nearest whole module and the two can differ -- reusing the
 * requested size here would misalign or stretch the image.
 */
internal fun renderQrPixels(
    bitmapMatrix: BitMatrix,
    paddingPx: Int,
): Pair<Int, IntArray> {
    val matrixSize = bitmapMatrix.width
    val finalSize = matrixSize + 2 * paddingPx
    val pixels = IntArray(finalSize * finalSize) { position ->
        val x = position % finalSize - paddingPx
        val y = position / finalSize - paddingPx
        if (x in 0 until matrixSize && y in 0 until matrixSize && bitmapMatrix.get(x, y)) {
            Color.BLACK
        } else {
            Color.WHITE
        }
    }
    return finalSize to pixels
}

/**
 * Whether this string is well-formed enough to justify generating a QR code for it: an absolute
 * `http`/`https` URL with a host. ZXing itself imposes no such requirement -- it will happily
 * encode any string, blank included -- so without this check, [buildQRBitmap] would produce a
 * technically valid QR code out of garbage input.
 */
internal fun String.isValidQrUrl(): Boolean {
    if (isBlank()) return false
    val uri = try {
        URI(this)
    } catch (_: URISyntaxException) {
        return false
    }
    return uri.isAbsolute &&
        uri.scheme?.lowercase().let { it == "http" || it == "https" } &&
        !uri.host.isNullOrBlank()
}

@Preview
@Composable
private fun PreviewQRImage() {
    QRImage("https://itur.cat/activities/1234567890")
}
