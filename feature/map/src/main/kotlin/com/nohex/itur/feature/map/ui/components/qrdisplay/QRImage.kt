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
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.collections.set

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
    val qrCodeWriter = QRCodeWriter()

    val encodeHints = mutableMapOf<EncodeHintType, Any?>()
        .apply {
            this[EncodeHintType.MARGIN] = paddingPx
        }

    try {
        val bitmapMatrix = qrCodeWriter.encode(
            qrData,
            BarcodeFormat.QR_CODE,
            sizePx,
            sizePx,
            encodeHints,
        )

        // Use the actual matrix dimensions to avoid aliasing from size mismatch.
        val matrixSize = bitmapMatrix.width
        val bitArray = IntArray(matrixSize * matrixSize) { position ->
            val x = position % matrixSize
            val y = position / matrixSize
            if (bitmapMatrix.get(x, y)) Color.BLACK else Color.WHITE
        }

        Bitmap.createBitmap(bitArray, matrixSize, matrixSize, Bitmap.Config.ARGB_8888)
    } catch (_: WriterException) {
        null
    }
}

@Preview
@Composable
private fun PreviewQRImage() {
    QRImage("https://itur.cat/activities/1234567890")
}
