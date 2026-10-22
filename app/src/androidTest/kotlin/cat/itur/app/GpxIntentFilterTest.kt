/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app

import android.content.Intent
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GpxIntentFilterTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val packageManager = context.packageManager

    @Test
    fun viewGpxWithKnownMimeTypesResolvesToItur() {
        listOf("application/gpx+xml", "application/octet-stream", "application/xml", "text/xml").forEach { mimeType ->
            assertTrue(isClaimedByItur(viewIntent("content://example/tracks/route.gpx", mimeType)))
        }
    }

    @Test
    fun viewGpxWithoutUsefulMimeTypeResolvesToItur() {
        assertTrue(isClaimedByItur(viewIntent("content://example/tracks/route.gpx", null)))
    }

    @Test
    fun unrelatedFilesAreNotClaimed() {
        assertFalse(isClaimedByItur(viewIntent("content://example/documents/notes.pdf", "application/pdf")))
        assertFalse(isClaimedByItur(viewIntent("content://example/documents/notes.bin", "application/octet-stream")))
    }

    private fun viewIntent(uri: String, mimeType: String?) = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        .addCategory(Intent.CATEGORY_DEFAULT)
        .apply { if (mimeType != null) setDataAndType(data, mimeType) }

    private fun isClaimedByItur(intent: Intent) = packageManager.queryIntentActivities(intent, 0)
        .any { it.activityInfo.packageName == context.packageName }
}
