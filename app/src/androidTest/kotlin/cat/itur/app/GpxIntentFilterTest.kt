/*
 * Itur © 2026 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app

import android.content.Intent
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GpxIntentFilterTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val packageManager = context.packageManager

    @Test
    fun viewGpxWithKnownMimeTypesResolvesToItur() {
        listOf("application/gpx+xml", "application/octet-stream", "application/xml", "text/xml").forEach { mimeType ->
            assertEquals(context.packageName, resolve(viewIntent("content://example/tracks/route.gpx", mimeType)))
        }
    }

    @Test
    fun viewGpxWithoutUsefulMimeTypeResolvesToItur() {
        assertEquals(context.packageName, resolve(viewIntent("content://example/tracks/route.gpx", null)))
    }

    @Test
    fun unrelatedFilesAreNotClaimed() {
        assertNull(resolve(viewIntent("content://example/documents/notes.pdf", "application/pdf")))
    }

    private fun viewIntent(uri: String, mimeType: String?) = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        .addCategory(Intent.CATEGORY_DEFAULT)
        .apply { if (mimeType != null) setDataAndType(data, mimeType) }

    private fun resolve(intent: Intent): String? = packageManager.resolveActivity(intent, 0)?.activityInfo?.packageName
}
