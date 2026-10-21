/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.core.data.repository

import android.content.Intent
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import cat.itur.app.core.model.GpxTrack
import cat.itur.app.core.model.MAX_GPX_BYTES
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class GpxIntentImporterTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun importsViewAndShareUrisReplacingTheMostRecentTrack() = runBlocking {
        val repository = InMemoryGpxTrackRepository()
        val first = gpxFile("first.gpx", gpx(1.0, 2.0))
        val second = gpxFile("second.gpx", gpx(3.0, 4.0))

        assertEquals(GpxImportResult.Imported, repository.importFrom(viewIntent(first), context.contentResolver))
        assertEquals(GpxImportResult.Imported, repository.importFrom(shareIntent(second), context.contentResolver))

        assertEquals(3.0, repository.track.value?.lines?.single()?.points?.single()?.latitude)
    }

    @Test
    fun rejectsMalformedAndOversizedUrisWithoutReplacingTheExistingTrack() = runBlocking {
        val repository = InMemoryGpxTrackRepository()
        repository.importFrom(viewIntent(gpxFile("existing.gpx", gpx(1.0, 2.0))), context.contentResolver)
        val existing: GpxTrack = requireNotNull(repository.track.value)

        assertEquals(
            GpxImportResult.Rejected,
            repository.importFrom(viewIntent(gpxFile("malformed.gpx", "not gpx")), context.contentResolver),
        )
        assertEquals(existing, repository.track.value)

        assertEquals(
            GpxImportResult.Rejected,
            repository.importFrom(viewIntent(gpxFile("oversized.gpx", " ".repeat(MAX_GPX_BYTES + 1))), context.contentResolver),
        )
        assertEquals(existing, repository.track.value)
    }

    @Test
    fun persistsImportedTrackAcrossRepositoryRecreation() = runBlocking {
        val repository = PersistedGpxTrackRepository(context)
        repository.setTrack(null)
        repository.importFrom(viewIntent(gpxFile("persisted.gpx", gpx(5.0, 6.0))), context.contentResolver)

        assertEquals(repository.track.value, PersistedGpxTrackRepository(context).track.value)
        repository.setTrack(null)
        assertNull(PersistedGpxTrackRepository(context).track.value)
    }

    private fun viewIntent(file: File) = Intent(Intent.ACTION_VIEW, Uri.fromFile(file))

    private fun shareIntent(file: File) = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file))

    private fun gpxFile(name: String, content: String): File = File(context.cacheDir, name).apply { writeText(content) }

    private fun gpx(latitude: Double, longitude: Double) = "<gpx xmlns=\"http://www.topografix.com/GPX/1/1\"><rte><rtept lat=\"$latitude\" lon=\"$longitude\"/></rte></gpx>"
}
