/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map

import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cat.itur.app.core.data.TestFixtures
import cat.itur.app.core.domain.id.UserId
import cat.itur.app.core.model.Location
import cat.itur.app.feature.map.config.LocationUpdateConfig
import cat.itur.app.feature.map.config.MapStyleConfig
import cat.itur.app.feature.map.notifications.BroadcastNotifier
import cat.itur.app.feature.map.ui.MapViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ParticipantLocationRefreshTest {

    @Test
    fun organizerAndParticipantSessionsProjectRawLocationsAcrossTwoRefreshes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val activities = ScenarioActivityRepository()
        val locations = ScenarioLocationRepository(activities)
        val unrelatedId = UserId("unrelated-user")
        val organizerGps = FakeLocationClient()
        val participantGps = FakeLocationClient()
        val organizerUsers = ScenarioUserRepository().apply { current = registered }
        val participantUsers = ScenarioUserRepository().apply { current = participant }
        lateinit var organizerSession: MapViewModel
        lateinit var participantSession: MapViewModel

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            runBlocking {
                activities.removeParticipant(
                    TestFixtures.ONGOING_ACTIVITY_ID,
                    TestFixtures.ORGANIZER_ID,
                )
                activities.addParticipant(
                    TestFixtures.ONGOING_ACTIVITY_ID,
                    TestFixtures.PARTICIPANT_1_ID,
                )
            }
            locations.seedLocation(
                TestFixtures.ONGOING_ACTIVITY_ID,
                unrelatedId,
                Location(latitude = 0.0, longitude = 0.0),
            )
            organizerSession = session(context, activities, organizerUsers, locations, organizerGps)
            participantSession = session(context, activities, participantUsers, locations, participantGps)
            runBlocking {
                organizerSession.triggerOngoingState(TestFixtures.ONGOING_ACTIVITY_ID, context)
                participantSession.triggerOngoingState(TestFixtures.ONGOING_ACTIVITY_ID, context)
            }
            organizerSession.startParticipantLocationMonitoring(refreshIntervalMillis = 100L)
            participantSession.startParticipantLocationMonitoring(refreshIntervalMillis = 100L)
        }

        awaitCondition("Both sessions did not complete two refreshes") {
            locations.readCount.get() >= 6
        }
        val initialExpectedIds = setOf(
            TestFixtures.ORGANIZER_ID,
            TestFixtures.PARTICIPANT_1_ID,
            TestFixtures.PARTICIPANT_2_ID,
        )
        assertEquals(
            initialExpectedIds,
            organizerSession.participantLocations.value.mapTo(mutableSetOf()) { it.userId },
        )
        assertEquals(
            initialExpectedIds,
            participantSession.participantLocations.value.mapTo(mutableSetOf()) { it.userId },
        )

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            runBlocking {
                activities.removeParticipant(
                    TestFixtures.ONGOING_ACTIVITY_ID,
                    TestFixtures.PARTICIPANT_1_ID,
                )
            }
        }

        awaitCondition("Both sessions retained the departed participant") {
            val organizerIds = organizerSession.participantLocations.value.map { it.userId }
            val participantIds = participantSession.participantLocations.value.map { it.userId }
            TestFixtures.PARTICIPANT_1_ID !in organizerIds &&
                TestFixtures.PARTICIPANT_1_ID !in participantIds
        }

        val refreshedExpectedIds = setOf(
            TestFixtures.ORGANIZER_ID,
            TestFixtures.PARTICIPANT_2_ID,
        )
        assertEquals(
            refreshedExpectedIds,
            organizerSession.participantLocations.value.mapTo(mutableSetOf()) { it.userId },
        )
        assertEquals(
            refreshedExpectedIds,
            participantSession.participantLocations.value.mapTo(mutableSetOf()) { it.userId },
        )
        assertTrue(unrelatedId !in organizerSession.participantLocations.value.map { it.userId })
        assertTrue(unrelatedId !in participantSession.participantLocations.value.map { it.userId })
        assertEquals("No session should need to emit a GPS fix", 0, locations.updateCount.get())

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            organizerSession.stopParticipantLocationMonitoring()
            participantSession.stopParticipantLocationMonitoring()
            organizerSession.stopLocationUpdates()
            participantSession.stopLocationUpdates()
        }
    }

    private fun awaitCondition(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 5_000L
        while (!condition() && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(20L)
        }
        assertTrue(message, condition())
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
