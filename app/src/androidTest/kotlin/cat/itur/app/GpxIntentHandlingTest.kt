/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.File

class GpxIntentHandlingTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun clearImportedTrack() {
        context.getSharedPreferences("gpx_track", 0).edit().clear().commit()
    }

    @Test
    fun importsAtColdStartAndWhileRunningWithoutReplacingTheActivity() {
        val coldStart = gpxFile("cold-start.gpx", 1.0, 2.0)
        val running = gpxFile("running.gpx", 3.0, 4.0)

        ActivityScenario.launch<MainActivity>(viewIntent(coldStart)).use { scenario ->
            waitForTrack("1.0,2.0")

            lateinit var activity: MainActivity
            scenario.onActivity {
                activity = it
            }
            context.startActivity(
                viewIntent(running)
                    .setClass(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )

            waitForTrack("3.0,4.0")
            scenario.onActivity { assertSame(activity, it) }
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
