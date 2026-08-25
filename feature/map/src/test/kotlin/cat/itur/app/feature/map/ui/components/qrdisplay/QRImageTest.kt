/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map.ui.components.qrdisplay

import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val SAMPLE_URL = "https://itur.cat/activities/abcdefghij0123456789"

/**
 * ZXing's own encoder ([QRCodeWriter]) is a plain-JVM class with no Android dependency, so these
 * tests exercise [renderQrPixels] against a genuinely encoded [BitMatrix] rather than a hand-built
 * fake one -- no Robolectric or device/emulator needed. [renderQrPixels] itself is kept free of
 * `android.graphics.Bitmap` for exactly this reason: constructing a real `Bitmap` does need one.
 */
class QRImageTest {
    private fun encode(
        data: String,
        sizePx: Int,
    ): BitMatrix = QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, sizePx, sizePx, mapOf(EncodeHintType.MARGIN to 4))

    @Test
    fun `GIVEN zero padding WHEN rendering THEN the final size matches the matrix and every pixel matches its bit`() {
        val matrix = encode(SAMPLE_URL, sizePx = 200)

        val (finalSize, pixels) = renderQrPixels(matrix, paddingPx = 0)

        assertEquals(matrix.width, finalSize)
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                val expected = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
                assertEquals(expected, pixels[y * finalSize + x], "mismatch at ($x, $y)")
            }
        }
    }

    @Test
    fun `GIVEN nonzero padding WHEN rendering THEN the final size grows by twice the padding`() {
        val matrix = encode("short", sizePx = 100)

        val (finalSize, _) = renderQrPixels(matrix, paddingPx = 12)

        assertEquals(matrix.width + 24, finalSize)
    }

    @Test
    fun `GIVEN nonzero padding WHEN rendering THEN the border is entirely white`() {
        val matrix = encode("short", sizePx = 100)
        val padding = 6

        val (finalSize, pixels) = renderQrPixels(matrix, paddingPx = padding)

        for (y in 0 until finalSize) {
            for (x in 0 until finalSize) {
                val inBorder = x < padding || y < padding || x >= finalSize - padding || y >= finalSize - padding
                if (inBorder) {
                    assertEquals(Color.WHITE, pixels[y * finalSize + x], "expected white border at ($x, $y)")
                }
            }
        }
    }

    @Test
    fun `GIVEN nonzero padding WHEN rendering THEN the interior still matches the original matrix`() {
        val matrix = encode(SAMPLE_URL, sizePx = 150)
        val padding = 8

        val (finalSize, pixels) = renderQrPixels(matrix, paddingPx = padding)

        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                val expected = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
                assertEquals(expected, pixels[(y + padding) * finalSize + (x + padding)], "mismatch at ($x, $y)")
            }
        }
    }

    @Test
    fun `WHEN the encoder's matrix size differs from the requested size THEN rendering still derives its size from the matrix`() {
        // ZXing rounds up to the nearest whole module, so the returned matrix width commonly
        // isn't exactly what was requested. This pins that renderQrPixels always derives its
        // output size from the actual matrix -- never the originally requested size -- which is
        // what avoids the aliasing/distortion this task is about.
        val requested = 137 // deliberately not a clean multiple of any module size
        val matrix = encode(SAMPLE_URL, sizePx = requested)

        val (finalSize, _) = renderQrPixels(matrix, paddingPx = 0)

        assertEquals(matrix.width, finalSize)
        assertTrue(matrix.width > 0)
    }

    // --- isValidQrUrl ---

    @Test
    fun `GIVEN an https URL WHEN validating THEN it is valid`() {
        assertTrue(SAMPLE_URL.isValidQrUrl())
    }

    @Test
    fun `GIVEN an http URL WHEN validating THEN it is valid`() {
        assertTrue("http://itur.cat/activities/abcdefghij0123456789".isValidQrUrl())
    }

    @Test
    fun `GIVEN a blank string WHEN validating THEN it is invalid`() {
        assertFalse("".isValidQrUrl())
        assertFalse("   ".isValidQrUrl())
    }

    @Test
    fun `GIVEN plain text with no scheme WHEN validating THEN it is invalid`() {
        assertFalse("not a url".isValidQrUrl())
    }

    @Test
    fun `GIVEN a scheme-relative URL WHEN validating THEN it is invalid`() {
        assertFalse("itur.cat/activities/abcdefghij0123456789".isValidQrUrl())
    }

    @Test
    fun `GIVEN a non-http scheme WHEN validating THEN it is invalid`() {
        assertFalse("ftp://itur.cat/activities/abcdefghij0123456789".isValidQrUrl())
        assertFalse("javascript:alert(1)".isValidQrUrl())
    }

    @Test
    fun `GIVEN an http URL with no host WHEN validating THEN it is invalid`() {
        assertFalse("http:///activities/abcdefghij0123456789".isValidQrUrl())
    }

    @Test
    fun `GIVEN a syntactically malformed URI WHEN validating THEN it is invalid`() {
        assertFalse("https://itur.cat/activities/ abcdefghij".isValidQrUrl())
    }
}
