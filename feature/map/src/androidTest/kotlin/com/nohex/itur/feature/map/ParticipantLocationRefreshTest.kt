/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.feature.map

import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nohex.itur.core.data.TestFixtures
import com.nohex.itur.feature.map.config.LocationUpdateConfig
import com.nohex.itur.feature.map.config.MapStyleConfig
import com.nohex.itur.feature.map.notifications.BroadcastNotifier
import com.nohex.itur.feature.map.ui.MapViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ParticipantLocationRefreshTest {

    @Test
    fun organizerSessionObservesParticipantLeaveWithoutOrganizerGpsFix() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val activities = ScenarioActivityRepository()
        val locations = ScenarioLocationRepository(activities)
        val organizerGps = FakeLocationClient()
        val participantGps = FakeLocationClient()
        val organizerUsers = ScenarioUserRepository().apply { current = registered }
        val participantUsers = ScenarioUserRepository().apply { current = participant }
        lateinit var organizerSession: MapViewModel
        lateinit var participantSession: MapViewModel

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            organizerSession = session(context, activities, organizerUsers, locations, organizerGps)
            participantSession = session(context, activities, participantUsers, locations, participantGps)
            runBlocking {
                organizerSession.triggerOngoingState(TestFixtures.ONGOING_ACTIVITY_ID, context)
                participantSession.triggerOngoingState(TestFixtures.ONGOING_ACTIVITY_ID, context)
            }
            organizerSession.startParticipantLocationMonitoring(refreshIntervalMillis = 100L)
            participantSession.leaveActivity()
        }

        val deadline = SystemClock.uptimeMillis() + 5_000L
        while (
            TestFixtures.PARTICIPANT_2_ID in organizerSession.participantLocations.value.map { it.userId } &&
            SystemClock.uptimeMillis() < deadline
        ) {
            SystemClock.sleep(20L)
        }

        assertTrue(
            "The organizer still showed the departed participant after the bounded refresh window",
            TestFixtures.PARTICIPANT_2_ID !in organizerSession.participantLocations.value.map { it.userId },
        )
        assertEquals("No session should need to emit a GPS fix", 0, locations.updateCount.get())

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            organizerSession.stopParticipantLocationMonitoring()
            organizerSession.stopLocationUpdates()
            participantSession.stopLocationUpdates()
        }
    }

    private fun session(
        context: Context,
        activities: ScenarioActivityRepository,
        users: ScenarioUserRepository,
        locations: ScenarioLocationRepository,
        locationClient: FakeLocationClient,
    ) = MapViewModel(
        activityRepository = activities,
        userRepository = users,
        locationsRepository = locations,
        locationClient = locationClient,
        broadcastNotifier = BroadcastNotifier(context),
        mapStyleConfig = MapStyleConfig(styleUrl = "https://example.invalid/style.json"),
        locationUpdateConfig = LocationUpdateConfig(updateIntervalMillis = 2_000L),
        backendHealthChecks = emptySet(),
    )
}
