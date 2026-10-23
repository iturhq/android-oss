/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app

import android.content.Intent
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class GpxIntentHandlingTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @After
    fun clearImportedTrack() {
        context.getSharedPreferences("gpx_track", 0).edit().clear().commit()
    }

    @Test
    fun importsAtColdStartAndWhileRunningWithoutReplacingTheActivity() {
        val coldStart = gpxFile("cold-start.gpx", 1.0, 2.0)
        val running = gpxFile("running.gpx", 3.0, 4.0)

        val activity = instrumentation.startActivitySync(
            viewIntent(coldStart)
                .setClass(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as MainActivity

        try {
            instrumentation.runOnMainSync {
                assertEquals(Intent.ACTION_VIEW, activity.intent.action)
                assertEquals(Uri.fromFile(coldStart), activity.intent.data)
            }
            waitForTrack("1.0,2.0")

            context.startActivity(
                viewIntent(running)
                    .setClass(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )

            waitForTrack("3.0,4.0")
            instrumentation.runOnMainSync {
                assertEquals(Uri.fromFile(running), activity.intent.data)
            }
        } finally {
            instrumentation.runOnMainSync { activity.finishAndRemoveTask() }
            instrumentation.waitForIdleSync()
        }
    }

    private fun viewIntent(file: File) = Intent(Intent.ACTION_VIEW, Uri.fromFile(file))

    private fun gpxFile(name: String, latitude: Double, longitude: Double): File = File(context.cacheDir, name).apply {
        writeText(
            "<gpx xmlns=\"http://www.topografix.com/GPX/1/1\"><rte><rtept lat=\"$latitude\" lon=\"$longitude\"/></rte></gpx>",
        )
    }

    private fun waitForTrack(expected: String) {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            if (context.getSharedPreferences("gpx_track", 0).getString("track", null)?.contains(expected) == true) return
            Thread.sleep(50)
        }
        throw AssertionError("Timed out waiting for imported GPX track $expected")
    }
}
