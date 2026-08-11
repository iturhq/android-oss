/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.feature.map.ui

import android.content.Context
import android.os.Looper
import com.nohex.itur.core.data.TestFixtures
import com.nohex.itur.core.data.health.BackendHealthCheck
import com.nohex.itur.core.data.health.BackendService
import com.nohex.itur.core.data.repository.ActivityFilter
import com.nohex.itur.core.data.repository.ActivityRepository
import com.nohex.itur.core.data.repository.DataResult
import com.nohex.itur.core.data.repository.FakeActivityRepository
import com.nohex.itur.core.data.repository.FakeLocationRepository
import com.nohex.itur.core.data.repository.LocationRepository
import com.nohex.itur.core.data.repository.FakeUserRepository
import com.nohex.itur.core.data.repository.UserRepository
import com.nohex.itur.core.domain.id.IturActivityId
import com.nohex.itur.core.domain.id.UserId
import com.nohex.itur.core.domain.model.User
import com.nohex.itur.core.location.LocationClient
import com.nohex.itur.core.model.Broadcast
import com.nohex.itur.core.model.IturActivity
import com.nohex.itur.core.model.IturActivityStatus
import com.nohex.itur.core.model.Location
import com.nohex.itur.feature.map.config.LocationUpdateConfig
import com.nohex.itur.feature.map.config.MapStyleConfig
import com.nohex.itur.feature.map.notifications.BroadcastNotifier
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.Date
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

// An activity where the organiser is a user that is NOT in FakeUserRepository's registered user
// (UserId("2")), so that the signed-in user is treated as a participant, not the organiser.
private val PARTICIPANT_ACTIVITY = TestFixtures.ongoingActivity.copy(
    id = IturActivityId("participantActv00001"),
    organizerId = UserId("other-organizer"),
    participantIds = emptyList(),
)

// A generous threshold (~111m) for distinguishing an unchanged stored location (which only
// moves by FakeLocationRepository's small "GPS noise" jitter, a few meters) from a location
// regenerated from scratch after removal (which lands near a different, unrelated fallback).
private const val LOCATION_UNCHANGED_THRESHOLD_DEGREES = 0.001

@RunWith(JUnit4::class)
class MapViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val context = mockk<Context>(relaxed = true)
    private val locationClient = mockk<LocationClient>(relaxed = true)
    private val broadcastNotifier = mockk<BroadcastNotifier>(relaxed = true)

    @Before
    fun setup() {
        mockkStatic(Looper::class)
        every { Looper.getMainLooper() } returns mockk(relaxed = true)
    }

    @After
    fun teardown() {
        unmockkStatic(Looper::class)
    }

    private fun activityRepo(vararg activities: IturActivity) = FakeActivityRepository(initialActivities = activities.toList())

    private fun userRepo() = FakeUserRepository()

    private fun locationRepo(activityRepo: ActivityRepository) = FakeLocationRepository(activityRepository = activityRepo)

    private fun viewModel(
        activityRepo: ActivityRepository = activityRepo(),
        userRepo: UserRepository = userRepo(),
        healthChecks: Set<BackendHealthCheck> = emptySet(),
        locationRepo: LocationRepository = locationRepo(activityRepo),
    ) = MapViewModel(
        activityRepository = activityRepo,
        userRepository = userRepo,
        locationsRepository = locationRepo,
        locationClient = locationClient,
        broadcastNotifier = broadcastNotifier,
        mapStyleConfig = MapStyleConfig(styleUrl = "https://example.invalid/style.json"),
        locationUpdateConfig = LocationUpdateConfig(updateIntervalMillis = 2_000L),
        backendHealthChecks = healthChecks,
    )

    /** Asserts the state is [MapUiState.Ongoing], showing the error message on failure. */
    private fun assertOngoing(vm: MapViewModel) {
        val state = vm.uiState.value
        if (state is MapUiState.Error) fail("Expected Ongoing but got Error: ${state.message}")
        assertIs<MapUiState.Ongoing>(state)
    }

    // --- init ---

    @Test
    fun `GIVEN no signed-in user WHEN ViewModel is created THEN currentUser is AnonymousUser`() {
        runTest {
            assertIs<User.AnonymousUser>(viewModel().currentUser.value)
        }
    }

    @Test
    fun `GIVEN a new ViewModel THEN initial uiState is Idle`() {
        runTest {
            assertIs<MapUiState.Idle>(viewModel().uiState.value)
        }
    }

    @Test
    fun `GIVEN no ongoing activity WHEN ViewModel is created THEN ongoingActivityId is null`() {
        runTest {
            assertNull(viewModel().ongoingActivityId.value)
        }
    }

    @Test
    fun `GIVEN a registered organizer with an ongoing activity WHEN ViewModel is created THEN ongoingActivityId is set`() {
        runTest {
            // FakeUserRepository's registered user is UserId("2") = TestFixtures.ORGANIZER_ID.
            val userRepo = userRepo()
            userRepo.signIn(context)
            val activityRepo = activityRepo(TestFixtures.ongoingActivity)
            val vm = viewModel(activityRepo = activityRepo, userRepo = userRepo)
            assertEquals(TestFixtures.ONGOING_ACTIVITY_ID, vm.ongoingActivityId.value)
        }
    }

    // --- signIn ---

    @Test
    fun `WHEN signing in THEN currentUser becomes a RegisteredUser`() {
        runTest {
            val vm = viewModel()
            vm.signIn(context)
            assertIs<User.RegisteredUser>(vm.currentUser.value)
        }
    }

    // --- signOut ---

    @Test
    fun `GIVEN a signed-in user WHEN signing out THEN currentUser becomes AnonymousUser`() {
        runTest {
            val vm = viewModel()
            vm.signIn(context)
            vm.signOut()
            assertIs<User.AnonymousUser>(vm.currentUser.value)
        }
    }

    @Test
    fun `WHEN signing out THEN uiState becomes Idle`() {
        runTest {
            val vm = viewModel()
            vm.signIn(context)
            vm.signOut()
            assertIs<MapUiState.Idle>(vm.uiState.value)
        }
    }

    // --- startActivity ---

    @Test
    fun `GIVEN an anonymous user WHEN starting an activity THEN user is signed in automatically`() {
        runTest {
            val vm = viewModel()
            vm.startActivity(context)
            assertIs<User.RegisteredUser>(vm.currentUser.value)
        }
    }

    @Test
    fun `GIVEN a registered user WHEN starting an activity THEN uiState becomes Ongoing`() {
        runTest {
            val vm = viewModel()
            vm.signIn(context)
            vm.startActivity(context)
            assertOngoing(vm)
        }
    }

    @Test
    fun `GIVEN a registered user WHEN starting an activity THEN ongoingActivityId is set`() {
        runTest {
            val vm = viewModel()
            vm.signIn(context)
            vm.startActivity(context)
            assertNotNull(vm.ongoingActivityId.value)
        }
    }

    // --- joinActivity ---

    @Test
    fun `GIVEN a registered user WHEN joining an existing activity THEN uiState becomes Ongoing`() {
        runTest {
            val userRepo = userRepo()
            userRepo.signIn(context)
            val activityRepo = activityRepo(TestFixtures.ongoingActivity)
            val vm = viewModel(activityRepo = activityRepo, userRepo = userRepo)
            vm.joinActivity(TestFixtures.ONGOING_ACTIVITY_ID, context)
            assertOngoing(vm)
        }
    }

    @Test
    fun `GIVEN a registered user WHEN joining an unknown activity THEN uiState becomes Error`() {
        runTest {
            val userRepo = userRepo()
            userRepo.signIn(context)
            val vm = viewModel(activityRepo = activityRepo(), userRepo = userRepo)
            vm.joinActivity(TestFixtures.ONGOING_ACTIVITY_ID, context)
            assertIs<MapUiState.Error>(vm.uiState.value)
        }
    }

    @Test
    fun `GIVEN session restoration is pending WHEN joining THEN current user is resolved`() {
        runTest {
            val healthGate = CompletableDeferred<Unit>()
            val delayedHealthCheck = object : BackendHealthCheck {
                override val service = BackendService("delayed", "Delayed test service")

                override suspend fun probe() {
                    healthGate.await()
                }

                override fun recognizes(cause: Throwable): Boolean = false
            }
            val activityRepo = activityRepo(TestFixtures.ongoingActivity)
            val vm = viewModel(
                activityRepo = activityRepo,
                healthChecks = setOf(delayedHealthCheck),
            )
            runCurrent()
            assertNull(vm.currentUser.value)

            vm.joinActivity(TestFixtures.ONGOING_ACTIVITY_ID, context)
            runCurrent()

            assertOngoing(vm)
            healthGate.complete(Unit)
            runCurrent()
        }
    }

    // --- MEMB-7A05: single-active-activity blocking ---

    @Test
    fun `GIVEN the organizer already has an ongoing activity WHEN starting another THEN uiState becomes Idle with a specific message`() {
        runTest {
            val userRepo = userRepo()
            userRepo.signIn(context)
            val activityRepo = activityRepo(TestFixtures.ongoingActivity)
            val vm = viewModel(activityRepo = activityRepo, userRepo = userRepo)

            vm.startActivity(context)

            val state = vm.uiState.value
            assertIs<MapUiState.Idle>(state)
            assertEquals("You're already in an activity -- leave it first", state.message)
        }
    }

    @Test
    fun `GIVEN the organizer already has an ongoing activity WHEN starting another THEN no new activity is created`() {
        runTest {
            val userRepo = userRepo()
            userRepo.signIn(context)
            val activityRepo = activityRepo(TestFixtures.ongoingActivity)
            val vm = viewModel(activityRepo = activityRepo, userRepo = userRepo)

            vm.startActivity(context)

            val activities = activityRepo.getActivities(ActivityFilter.ByOrganizer(TestFixtures.ORGANIZER_ID))
            assertIs<DataResult.Success<List<IturActivity>>>(activities)
            assertEquals(1, activities.data.size)
        }
    }

    @Test
    fun `GIVEN a participant already active in a different ongoing activity WHEN joining another THEN uiState becomes Idle with a specific message`() {
        runTest {
            val userRepo = userRepo()
            userRepo.signIn(context)
            val activityRepo = activityRepo(TestFixtures.ongoingActivity, PARTICIPANT_ACTIVITY)
            val vm = viewModel(activityRepo = activityRepo, userRepo = userRepo)

            vm.joinActivity(PARTICIPANT_ACTIVITY.id, context)

            val state = vm.uiState.value
            assertIs<MapUiState.Idle>(state)
            assertEquals("You're already in an activity -- leave it first", state.message)
        }
    }

    @Test
    fun `GIVEN a participant already active in a different ongoing activity WHEN joining another THEN they are not added as a participant`() {
        runTest {
            val userRepo = userRepo()
            userRepo.signIn(context)
            val activityRepo = activityRepo(TestFixtures.ongoingActivity, PARTICIPANT_ACTIVITY)
            val vm = viewModel(activityRepo = activityRepo, userRepo = userRepo)

            vm.joinActivity(PARTICIPANT_ACTIVITY.id, context)

            val updated = activityRepo.getActivity(PARTICIPANT_ACTIVITY.id)
            assertIs<DataResult.Success<IturActivity>>(updated)
            assertTrue(TestFixtures.ORGANIZER_ID !in updated.data.participantIds)
        }
    }

    @Test
    fun `GIVEN a user already an active member of an activity WHEN re-joining that same activity THEN it is not blocked`() {
        runTest {
            val userRepo = userRepo()
            userRepo.signIn(context)
            val activityRepo = activityRepo(TestFixtures.ongoingActivity)
            val vm = viewModel(activityRepo = activityRepo, userRepo = userRepo)

            vm.joinActivity(TestFixtures.ONGOING_ACTIVITY_ID, context)

            assertOngoing(vm)
        }
    }

    @Test
    fun `GIVEN the organizer's only membership is a DRAFT activity WHEN starting a new one THEN it is not blocked`() {
        runTest {
            val userRepo = userRepo()
            userRepo.signIn(context)
            val activityRepo = activityRepo(TestFixtures.draftActivity)
            val vm = viewModel(activityRepo = activityRepo, userRepo = userRepo)

            vm.startActivity(context)

            assertOngoing(vm)
        }
    }

    // --- leaveActivity ---

    @Test
    fun `GIVEN no local GPS fix WHEN the participant refresh interval elapses THEN markers refresh`() = runTest {
        val activityRepo = activityRepo(TestFixtures.ongoingActivity)
        val initialLocations = TestFixtures.ongoingActivityLocations
        val refreshedLocations = initialLocations.dropLast(1)
        val locationRepo = mockk<LocationRepository>()
        coEvery { locationRepo.getForActivity(TestFixtures.ONGOING_ACTIVITY_ID) } returns
            initialLocations andThen refreshedLocations
        val userRepo = userRepo().also { it.signIn(context) }
        val vm = viewModel(
            activityRepo = activityRepo,
            userRepo = userRepo,
            locationRepo = locationRepo,
        )
        vm.triggerOngoingState(TestFixtures.ONGOING_ACTIVITY_ID, context)
        assertEquals(initialLocations, vm.participantLocations.value)

        vm.startParticipantLocationMonitoring(refreshIntervalMillis = 1_000L)
        try {
            advanceTimeBy(999L)
            runCurrent()
            assertEquals(initialLocations, vm.participantLocations.value)

            advanceTimeBy(2L)
            runCurrent()
            assertEquals(refreshedLocations, vm.participantLocations.value)
            coVerify(exactly = 0) {
                locationRepo.updateForParticipant(
                    TestFixtures.ORGANIZER_ID,
                    TestFixtures.ONGOING_ACTIVITY_ID,
                    any(),
                )
            }
        } finally {
            vm.stopParticipantLocationMonitoring()
        }
    }

    @Test
    fun `GIVEN no ongoing activity WHEN leaving THEN uiState remains Idle`() {
        runTest {
            val vm = viewModel()
            vm.leaveActivity()
            assertIs<MapUiState.Idle>(vm.uiState.value)
        }
    }

    @Test
    fun `GIVEN an ongoing activity as organizer WHEN leaving THEN activity is marked FINISHED and uiState is Idle`() {
        runTest {
            val activityRepo = activityRepo()
            val vm = viewModel(activityRepo = activityRepo)
            // startActivity auto-signs in and creates an activity with the current user as organizer.
            vm.startActivity(context)
            val activityId = vm.ongoingActivityId.value!!

            vm.leaveActivity()

            val result = activityRepo.getActivity(activityId)
            assertIs<DataResult.Success<IturActivity>>(result)
            assertEquals(IturActivityStatus.FINISHED, result.data.status)
            assertIs<MapUiState.Idle>(vm.uiState.value)
        }
    }

    @Test
    fun `GIVEN an ongoing activity as participant WHEN leaving THEN participant is removed and uiState is Idle`() {
        runTest {
            val userRepo = userRepo()
            val signedInUser = userRepo.signIn(context)
            val activityRepo = activityRepo(PARTICIPANT_ACTIVITY)
            val vm = viewModel(activityRepo = activityRepo, userRepo = userRepo)

            vm.joinActivity(PARTICIPANT_ACTIVITY.id, context)
            assertOngoing(vm)

            vm.leaveActivity()

            val result = activityRepo.getActivity(PARTICIPANT_ACTIVITY.id)
            assertIs<DataResult.Success<IturActivity>>(result)
            // Participant leaving does not finish the activity.
            assertNotEquals(IturActivityStatus.FINISHED, result.data.status)
            // The participant is no longer in the list.
            assertFalse(signedInUser.id in result.data.participantIds)
            assertIs<MapUiState.Idle>(vm.uiState.value)
        }
    }

    @Test
    fun `GIVEN an ongoing activity as participant WHEN leaving THEN other participants' locations are not cleared`() {
        runTest {
            // A second, still-in-the-activity participant whose location must survive.
            val otherParticipantId = TestFixtures.PARTICIPANT_2_ID
            val otherParticipantLocation = Location(latitude = 51.0, longitude = 0.0)

            val userRepo = userRepo()
            val signedInUser = userRepo.signIn(context)
            val activityRepo = activityRepo(PARTICIPANT_ACTIVITY.copy(participantIds = listOf(otherParticipantId)))
            val locationRepo = locationRepo(activityRepo)
            val vm = viewModel(activityRepo = activityRepo, userRepo = userRepo, locationRepo = locationRepo)

            vm.joinActivity(PARTICIPANT_ACTIVITY.id, context)
            assertOngoing(vm)

            locationRepo.updateForParticipant(otherParticipantId, PARTICIPANT_ACTIVITY.id, otherParticipantLocation)
            locationRepo.updateForParticipant(
                signedInUser.id,
                PARTICIPANT_ACTIVITY.id,
                Location(latitude = 52.0, longitude = 1.0),
            )

            vm.leaveActivity()

            // The other participant's stored location is unchanged (within the fake repo's
            // small "GPS noise" jitter), proving it was not wiped by the leaving participant.
            val survivingLocation = locationRepo.getForActivity(PARTICIPANT_ACTIVITY.id)
                .first { it.userId == otherParticipantId }
                .location
            assertTrue(abs(survivingLocation.latitude - otherParticipantLocation.latitude) < LOCATION_UNCHANGED_THRESHOLD_DEGREES)
            assertTrue(abs(survivingLocation.longitude - otherParticipantLocation.longitude) < LOCATION_UNCHANGED_THRESHOLD_DEGREES)
        }
    }

    @Test
    fun `GIVEN an ongoing activity as organizer WHEN leaving THEN all participants' locations are cleared`() {
        runTest {
            val activityRepo = activityRepo()
            val locationRepo = locationRepo(activityRepo)
            val vm = viewModel(activityRepo = activityRepo, locationRepo = locationRepo)
            // startActivity auto-signs in and creates an activity with the current user as organizer.
            vm.startActivity(context)
            val activityId = vm.ongoingActivityId.value!!
            val organizerId = vm.currentUser.value!!.id

            // Add another participant to the activity and seed a location for them.
            val otherParticipantId = UserId("other-participant")
            activityRepo.addParticipant(activityId, otherParticipantId)
            val otherParticipantLocation = Location(latitude = 51.0, longitude = 0.0)
            locationRepo.updateForParticipant(otherParticipantId, activityId, otherParticipantLocation)
            locationRepo.updateForParticipant(organizerId, activityId, Location(latitude = 52.0, longitude = 1.0))

            vm.leaveActivity()

            // With no stored location left, getForActivity regenerates a fresh position from
            // scratch (near the default fallback location), which lands far away from the
            // location that was seeded above -- proving the original record was cleared.
            val regeneratedLocation = locationRepo.getForActivity(activityId)
                .first { it.userId == otherParticipantId }
                .location
            assertTrue(
                abs(regeneratedLocation.latitude - otherParticipantLocation.latitude) > LOCATION_UNCHANGED_THRESHOLD_DEGREES,
            )
        }
    }

    // --- triggerIdleState ---

    @Test
    fun `WHEN triggerIdleState is called with a message THEN uiState is Idle with that message`() {
        runTest {
            val vm = viewModel()
            vm.triggerIdleState("Test message")
            val state = assertIs<MapUiState.Idle>(vm.uiState.value)
            assertEquals("Test message", state.message)
        }
    }

    @Test
    fun `WHEN triggerIdleState is called THEN ongoingActivityId is null`() {
        runTest {
            val vm = viewModel()
            vm.startActivity(context)
            assertNotNull(vm.ongoingActivityId.value)
            vm.triggerIdleState()
            assertNull(vm.ongoingActivityId.value)
        }
    }

    // --- requestAttention ---

    @Test
    fun `GIVEN no ongoing activity WHEN requesting attention THEN uiState remains Idle`() {
        runTest {
            val vm = viewModel()
            vm.requestAttention()
            assertIs<MapUiState.Idle>(vm.uiState.value)
        }
    }

    @Test
    fun `GIVEN an ongoing activity WHEN requesting attention THEN no exception is thrown`() {
        runTest {
            val vm = viewModel()
            vm.startActivity(context)
            vm.requestAttention()
        }
    }

    // --- pollBroadcastsOnce (UC-ACTIVITY-007) ---

    @Test
    fun `GIVEN a broadcast already sent WHEN polling THEN latestBroadcast reflects it and the notifier is called`() {
        runTest {
            val userRepo = userRepo()
            userRepo.signIn(context)
            val activityRepo = activityRepo(PARTICIPANT_ACTIVITY)
            val broadcast = Broadcast(id = "b1", message = "Return to the meeting point", sentOn = Date())
            activityRepo.addBroadcast(PARTICIPANT_ACTIVITY.id, broadcast)

            val vm = viewModel(activityRepo = activityRepo, userRepo = userRepo)
            vm.joinActivity(PARTICIPANT_ACTIVITY.id, context)
            vm.pollBroadcastsOnce()

            assertEquals(broadcast, vm.latestBroadcast.value)
            verify { broadcastNotifier.notify(broadcast) }
        }
    }

    @Test
    fun `GIVEN no broadcasts WHEN polling THEN latestBroadcast stays null`() {
        runTest {
            val userRepo = userRepo()
            userRepo.signIn(context)
            val activityRepo = activityRepo(PARTICIPANT_ACTIVITY)

            val vm = viewModel(activityRepo = activityRepo, userRepo = userRepo)
            vm.joinActivity(PARTICIPANT_ACTIVITY.id, context)
            vm.pollBroadcastsOnce()

            assertNull(vm.latestBroadcast.value)
        }
    }

    @Test
    fun `GIVEN no ongoing activity WHEN polling THEN no exception is thrown`() {
        runTest {
            viewModel().pollBroadcastsOnce()
        }
    }

    @Test
    fun `WHEN triggerIdleState is called THEN latestBroadcast is cleared`() {
        runTest {
            val userRepo = userRepo()
            userRepo.signIn(context)
            val activityRepo = activityRepo(PARTICIPANT_ACTIVITY)
            val broadcast = Broadcast(id = "b1", message = "hello", sentOn = Date())
            activityRepo.addBroadcast(PARTICIPANT_ACTIVITY.id, broadcast)

            val vm = viewModel(activityRepo = activityRepo, userRepo = userRepo)
            vm.joinActivity(PARTICIPANT_ACTIVITY.id, context)
            vm.pollBroadcastsOnce()
            assertEquals(broadcast, vm.latestBroadcast.value)

            vm.triggerIdleState()

            assertNull(vm.latestBroadcast.value)
        }
    }

    // --- service-aware availability / retry backoff ---

    @Test
    fun `GIVEN a service is unreachable at startup THEN availability names it with the first countdown`() = runTest {
        val firestore = FakeBackendHealthCheck("firestore", "Cloud Firestore", available = false)
        val vm = viewModel(healthChecks = setOf(firestore))

        vm.startBackendMonitoring()

        assertEquals(
            BackendAvailabilityUiState(
                failingServices = listOf(firestore.service),
                retryCountdown = 5,
            ),
            vm.backendAvailability.value,
        )
        vm.stopBackendMonitoring()
    }

    @Test
    fun `GIVEN a service probe times out THEN availability names it as failed`() = runTest {
        val hangingService = object : BackendHealthCheck {
            override val service = BackendService("hanging", "Hanging service")

            override suspend fun probe() {
                kotlinx.coroutines.awaitCancellation()
            }
        }
        val vm = viewModel(healthChecks = setOf(hangingService))

        vm.startBackendMonitoring()
        mainDispatcherRule.testDispatcher.scheduler.advanceTimeBy(5_001L)
        runCurrent()

        assertEquals(
            listOf(hangingService.service),
            vm.backendAvailability.value.failingServices,
        )
        vm.stopBackendMonitoring()
    }

    @Test
    fun `GIVEN an in-flight probe WHEN monitoring stops THEN genuine cancellation propagates`() = runTest {
        var cancellationObserved = false
        val hangingService = object : BackendHealthCheck {
            override val service = BackendService("hanging", "Hanging service")

            override suspend fun probe() {
                try {
                    kotlinx.coroutines.awaitCancellation()
                } finally {
                    cancellationObserved = true
                }
            }
        }
        val vm = viewModel(healthChecks = setOf(hangingService))
        vm.startBackendMonitoring()
        runCurrent()

        vm.stopBackendMonitoring()
        runCurrent()

        assertTrue(cancellationObserved)
        assertEquals(BackendAvailabilityUiState(), vm.backendAvailability.value)
    }

    @Test
    fun `GIVEN multiple services fail THEN all independently named failures are aggregated`() = runTest {
        val auth = FakeBackendHealthCheck("auth", "Firebase Authentication", false)
        val firestore = FakeBackendHealthCheck("firestore", "Cloud Firestore", false)

        val vm = viewModel(healthChecks = setOf(firestore, auth))

        assertEquals(
            listOf(auth.service, firestore.service),
            vm.backendAvailability.value.failingServices,
        )
    }

    @Test
    fun `GIVEN one failed service recovers THEN retry keeps only the remaining failure`() = runTest {
        val auth = FakeBackendHealthCheck("auth", "Firebase Authentication", false)
        val firestore = FakeBackendHealthCheck("firestore", "Cloud Firestore", false)
        val vm = viewModel(healthChecks = setOf(auth, firestore))
        auth.available = true

        vm.retryNow()

        assertEquals(
            listOf(firestore.service),
            vm.backendAvailability.value.failingServices,
        )
    }

    @Test
    fun `GIVEN the backend recovers WHEN retryNow is called THEN overlay clears immediately`() = runTest {
        val firestore = FakeBackendHealthCheck("firestore", "Cloud Firestore", false)
        val vm = viewModel(healthChecks = setOf(firestore))
        firestore.available = true

        vm.retryNow()

        assertEquals(BackendAvailabilityUiState(), vm.backendAvailability.value)
        assertIs<MapUiState.Idle>(vm.uiState.value)
    }

    @Test
    fun `GIVEN a failed service recovers during backoff THEN automatic retry clears the overlay`() = runTest {
        val firestore = FakeBackendHealthCheck("firestore", "Cloud Firestore", false)
        val vm = viewModel(healthChecks = setOf(firestore))
        vm.startBackendMonitoring()
        firestore.available = true

        mainDispatcherRule.testDispatcher.scheduler.advanceTimeBy(5_001L)
        runCurrent()

        assertEquals(BackendAvailabilityUiState(), vm.backendAvailability.value)
        assertIs<User.AnonymousUser>(vm.currentUser.value)
        vm.stopBackendMonitoring()
    }

    @Test
    fun `GIVEN an ongoing activity WHEN a service fails THEN the operation state is preserved and resumes`() = runTest {
        val firestore = FakeBackendHealthCheck("firestore", "Cloud Firestore")
        val userRepo = userRepo().also { it.signIn(context) }
        val activityRepo = activityRepo(TestFixtures.ongoingActivity)
        val vm = viewModel(
            activityRepo = activityRepo,
            userRepo = userRepo,
            healthChecks = setOf(firestore),
        )
        vm.joinActivity(TestFixtures.ONGOING_ACTIVITY_ID, context)
        val ongoing = assertIs<MapUiState.Ongoing>(vm.uiState.value)
        firestore.available = false

        vm.retryNow()

        assertEquals(listOf(firestore.service), vm.backendAvailability.value.failingServices)
        assertEquals(ongoing, vm.uiState.value)
        firestore.available = true
        vm.retryNow()
        assertEquals(BackendAvailabilityUiState(), vm.backendAvailability.value)
        assertEquals(ongoing, vm.uiState.value)
    }

    @Test
    fun `GIVEN ongoing activity WHEN operation reports recognized failure THEN overlay updates immediately`() = runTest {
        val firestore = FakeBackendHealthCheck(
            id = "firestore",
            displayName = "Cloud Firestore",
            recognizesCause = true,
        )
        val activityRepo = mockk<ActivityRepository>()
        coEvery { activityRepo.getActivities(any()) } returns DataResult.Success(emptyList())
        coEvery { activityRepo.getActiveActivityId(TestFixtures.ORGANIZER_ID) } returns DataResult.Success(null)
        coEvery {
            activityRepo.addParticipant(
                TestFixtures.ONGOING_ACTIVITY_ID,
                TestFixtures.ORGANIZER_ID,
            )
        } returns DataResult.Success(TestFixtures.ongoingActivity)
        coEvery { activityRepo.getActivity(TestFixtures.ONGOING_ACTIVITY_ID) } returns
            DataResult.Success(TestFixtures.ongoingActivity)
        coEvery {
            activityRepo.requestAttention(
                TestFixtures.ONGOING_ACTIVITY_ID,
                TestFixtures.ORGANIZER_ID,
            )
        } throws IllegalStateException("offline")
        val userRepo = userRepo().also { it.signIn(context) }
        val vm = viewModel(
            activityRepo = activityRepo,
            userRepo = userRepo,
            healthChecks = setOf(firestore),
        )
        vm.joinActivity(TestFixtures.ONGOING_ACTIVITY_ID, context)
        val ongoing = assertIs<MapUiState.Ongoing>(vm.uiState.value)

        vm.requestAttention()

        assertEquals(listOf(firestore.service), vm.backendAvailability.value.failingServices)
        assertEquals(ongoing, vm.uiState.value)
    }

    @Test
    fun `WHEN monitoring stops THEN retry and cadence work are cancelled`() = runTest {
        val firestore = FakeBackendHealthCheck("firestore", "Cloud Firestore", false)
        val vm = viewModel(healthChecks = setOf(firestore))
        vm.startBackendMonitoring()
        val probesBeforeExit = firestore.probeCount

        vm.stopBackendMonitoring()
        firestore.available = true
        mainDispatcherRule.testDispatcher.scheduler.advanceTimeBy(60_000L)
        runCurrent()

        assertEquals(probesBeforeExit, firestore.probeCount)
        assertEquals(listOf(firestore.service), vm.backendAvailability.value.failingServices)
    }

    // --- triggerOngoingState(activityId, context) recoverable errors ---

    @Test
    fun `GIVEN an activity that can no longer be found WHEN triggering its ongoing state THEN uiState becomes a RecoverableError`() {
        runTest {
            val activityRepo = mockk<ActivityRepository>()
            coEvery { activityRepo.getActivities(any()) } returns DataResult.Success(emptyList())
            coEvery { activityRepo.getActivity(TestFixtures.ONGOING_ACTIVITY_ID) } returns DataResult.NotFound(TestFixtures.ONGOING_ACTIVITY_ID.value)

            val vm = viewModel(activityRepo = activityRepo)
            vm.triggerOngoingState(TestFixtures.ONGOING_ACTIVITY_ID, context)

            val state = assertIs<MapUiState.RecoverableError>(vm.uiState.value)
            assertEquals("The ongoing activity could not be resumed.", state.message)
        }
    }

    @Test
    fun `GIVEN a RecoverableError WHEN onCancel is invoked THEN uiState returns to Idle`() {
        runTest {
            val activityRepo = mockk<ActivityRepository>()
            coEvery { activityRepo.getActivities(any()) } returns DataResult.Success(emptyList())
            coEvery { activityRepo.getActivity(TestFixtures.ONGOING_ACTIVITY_ID) } returns DataResult.Error("backend error")

            val vm = viewModel(activityRepo = activityRepo)
            vm.triggerOngoingState(TestFixtures.ONGOING_ACTIVITY_ID, context)
            val state = assertIs<MapUiState.RecoverableError>(vm.uiState.value)

            state.onCancel()

            assertIs<MapUiState.Idle>(vm.uiState.value)
        }
    }

    @Test
    fun `GIVEN a RecoverableError WHEN onRetry succeeds THEN uiState becomes Ongoing`() {
        runTest {
            val activityRepo = mockk<ActivityRepository>()
            coEvery { activityRepo.getActivities(any()) } returns DataResult.Success(emptyList())
            coEvery { activityRepo.getActivity(TestFixtures.ONGOING_ACTIVITY_ID) } returns
                DataResult.Error("backend error") andThen
                DataResult.Success(TestFixtures.ongoingActivity)

            val vm = viewModel(activityRepo = activityRepo)
            vm.triggerOngoingState(TestFixtures.ONGOING_ACTIVITY_ID, context)
            val state = assertIs<MapUiState.RecoverableError>(vm.uiState.value)

            state.onRetry()

            assertOngoing(vm)
        }
    }

    // --- startActivity failure ---

    @Test
    fun `GIVEN the backend rejects activity creation WHEN starting an activity THEN uiState becomes Error`() {
        runTest {
            val activityRepo = mockk<ActivityRepository>()
            coEvery { activityRepo.getActivities(any()) } returns DataResult.Success(emptyList())
            coEvery { activityRepo.getActiveActivityId(TestFixtures.ORGANIZER_ID) } returns DataResult.Success(null)
            coEvery { activityRepo.createActivity(TestFixtures.ORGANIZER_ID) } returns DataResult.Error("quota exceeded")

            val vm = viewModel(activityRepo = activityRepo)
            vm.startActivity(context)

            val state = assertIs<MapUiState.Error>(vm.uiState.value)
            assertEquals("quota exceeded", state.message)
        }
    }
}

private class FakeBackendHealthCheck(
    id: String,
    displayName: String,
    var available: Boolean = true,
    private val recognizesCause: Boolean = false,
) : BackendHealthCheck {
    override val service = BackendService(id, displayName)
    var probeCount: Int = 0
        private set

    override suspend fun probe() {
        probeCount++
        if (!available) throw IllegalStateException("${service.id} unavailable")
    }

    override fun recognizes(cause: Throwable): Boolean = recognizesCause
}
