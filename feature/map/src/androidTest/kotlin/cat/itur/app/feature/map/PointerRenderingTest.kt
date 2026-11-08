/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cat.itur.app.core.model.ParticipantSignal
import cat.itur.app.feature.map.ui.components.map.PointerRole
import cat.itur.app.feature.map.ui.components.map.pointerPresentation
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PointerRenderingTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun directionalPointerShaftsRenderBlueOrangeAndRedByStatus() {
        val okay = render(signal = null)
        val delayed = render(ParticipantSignal.DELAYED)
        val needsHelp = render(ParticipantSignal.NEEDS_HELP)

        assertTrue("Okay pointer must visibly render a blue shaft", okay.count(::isBlue) > 100)
        assertTrue("Delayed pointer must visibly render an orange shaft", delayed.count(::isOrange) > 100)
        assertTrue("Help pointer must visibly render a red shaft", needsHelp.count(::isRed) > 100)
        assertTrue("Delayed pointer retains its non-colour pause cue", delayed.count(::isWhite) > 20)
        assertTrue("Help pointer retains its non-colour exclamation cue", needsHelp.count(::isWhite) > 20)
    }

    private fun render(signal: ParticipantSignal?): IntArray {
        val drawableId = pointerPresentation(PointerRole.OTHER, signal, directional = true).drawable
        val drawable = checkNotNull(ContextCompat.getDrawable(context, drawableId))
        val bitmap = Bitmap.createBitmap(RENDER_SIZE, RENDER_SIZE, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, RENDER_SIZE, RENDER_SIZE)
        drawable.draw(Canvas(bitmap))
        return IntArray(RENDER_SIZE * RENDER_SIZE).also { pixels ->
            bitmap.getPixels(pixels, 0, RENDER_SIZE, 0, 0, RENDER_SIZE, RENDER_SIZE)
            bitmap.recycle()
        }
    }

    private fun isBlue(pixel: Int): Boolean = Color.alpha(pixel) > 100 &&
        Color.blue(pixel) > 140 && Color.blue(pixel) > Color.red(pixel) * 1.3 &&
        Color.blue(pixel) > Color.green(pixel) * 1.1

    private fun isOrange(pixel: Int): Boolean = Color.alpha(pixel) > 100 &&
        Color.red(pixel) > 180 && Color.green(pixel) in 70..180 && Color.blue(pixel) < 80

    private fun isRed(pixel: Int): Boolean = Color.alpha(pixel) > 100 &&
        Color.red(pixel) > 140 && Color.green(pixel) < 100 && Color.blue(pixel) < 100

    private fun isWhite(pixel: Int): Boolean = Color.alpha(pixel) > 200 &&
        Color.red(pixel) > 220 && Color.green(pixel) > 220 && Color.blue(pixel) > 220

    private companion object {
        const val RENDER_SIZE = 56
    }
}
