/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map.ui

import android.content.Context
import android.os.Looper
import cat.itur.app.core.data.TestFixtures
import cat.itur.app.core.data.health.BackendHealthCheck
import cat.itur.app.core.data.health.BackendService
import cat.itur.app.core.data.repository.ActivityFilter
import cat.itur.app.core.data.repository.ActivityRepository
import cat.itur.app.core.data.repository.DataResult
import cat.itur.app.core.data.repository.FakeActivityRepository
import cat.itur.app.core.data.repository.FakeLocationRepository
import cat.itur.app.core.data.repository.FakeUserRepository
import cat.itur.app.core.data.repository.LocationObservationScope
import cat.itur.app.core.data.repository.LocationReportMeasurement
import cat.itur.app.core.data.repository.LocationReportOutcome
import cat.itur.app.core.data.repository.LocationReportStage
import cat.itur.app.core.data.repository.LocationRepository
import cat.itur.app.core.data.repository.ParticipantSignalRepository
import cat.itur.app.core.data.repository.SignInFailureReason
import cat.itur.app.core.data.repository.SignInResult
import cat.itur.app.core.data.repository.UserRepository
import cat.itur.app.core.domain.id.IturActivityId
import cat.itur.app.core.domain.id.UserId
import cat.itur.app.core.domain.model.User
import cat.itur.app.core.location.LocationClient
import cat.itur.app.core.model.Broadcast
import cat.itur.app.core.model.IturActivity
import cat.itur.app.core.model.IturActivityStatus
import cat.itur.app.core.model.Location
import cat.itur.app.core.model.ParticipantLocation
import cat.itur.app.core.model.ParticipantSignal
import cat.itur.app.feature.map.config.LocationUpdateConfig
import cat.itur.app.feature.map.config.MapStyleConfig
import cat.itur.app.feature.map.notifications.BroadcastNotifier
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.Date
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
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

private fun IturActivity.locationObservationScope() = LocationObservationScope(
    activityId = id,
    organizerId = organizerId,
    participantIds = participantIds,
)

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

    @Test
    fun `GIVEN a modern participant reservation WHEN ViewModel is created THEN ongoing activity restores without the legacy array query`() = runTest {
        val participantId = UserId("modern-participant")
        val userRepo = mockk<UserRepository>(relaxed = true)
        coEvery { userRepo.getCurrentUser() } returns User.RegisteredUser(participantId, null, null)
        val activityRepo = mockk<ActivityRepository>(relaxed = true)
        coEvery { activityRepo.getActiveActivityId(participantId) } returns
            DataResult.Success(PARTICIPANT_ACTIVITY.id)
        coEvery { activityRepo.getActivity(PARTICIPANT_ACTIVITY.id) } returns
            DataResult.Success(PARTICIPANT_ACTIVITY)

        val vm = viewModel(activityRepo = activityRepo, userRepo = userRepo)

        assertEquals(PARTICIPANT_ACTIVITY.id, vm.ongoingActivityId.value)
        coVerify(exactly = 0) { activityRepo.getActivities(any()) }
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

    @Test
    fun `GIVEN user cancellation WHEN signing in THEN prior state is preserved and no failure is presented`() = runTest {
        val vm = viewModel(userRepo = ScriptedSignInUserRepository(SignInResult.Cancelled))
        val previousState = vm.uiState.value

        vm.signIn(context)

        assertSame(previousState, vm.uiState.value)
        assertNull(vm.signInPresentation.value)
        assertIs<User.AnonymousUser>(vm.currentUser.value)
    }

    @Test
    fun `GIVEN classified failures WHEN signing in THEN stable product copy replaces provider detail`() = runTest {
        val expected = mapOf(
            SignInFailureReason.NO_ACCOUNT to
                "No Google account is available. Add an account and try again.",
            SignInFailureReason.NOT_CONFIGURED to
                "Sign-in isn't configured for this app.",
            SignInFailureReason.SERVICE_UNAVAILABLE to
                "Sign-in is temporarily unavailable. Check your connection and try again.",
            SignInFailureReason.UNEXPECTED to
                "Sign-in couldn't be completed. Try again.",
        )

        expected.forEach { (reason, message) ->
            val vm = viewModel(
                userRepo = ScriptedSignInUserRepository(SignInResult.Failure(reason)),
            )
            val previousState = vm.uiState.value

            vm.signIn(context)

            val presentation = assertNotNull(vm.signInPresentation.value)
            assertSame(previousState, vm.uiState.value)
            assertEquals(message, presentation.message)
            assertEquals(reason != SignInFailureReason.NOT_CONFIGURED, presentation.retryable)
            listOf(
                "Requests from this Android client application cat.itur.app.pro are blocked",
                "FirebaseAuthException",
                "api_key=provider-secret",
                "token=provider-token",
            ).forEach { rawDetail -> assertFalse(rawDetail in presentation.message) }
        }
    }

    @Test
    fun `GIVEN a retryable sign-in failure WHEN retrying THEN success updates the user`() = runTest {
        val registered = User.RegisteredUser(UserId("2"), "Test User", "test@example.com")
        val vm = viewModel(
            userRepo = ScriptedSignInUserRepository(
                SignInResult.Failure(SignInFailureReason.SERVICE_UNAVAILABLE),
                SignInResult.Success(registered),
            ),
        )
        vm.signIn(context)

        assertNotNull(vm.signInPresentation.value).onRetry()
        runCurrent()

        assertEquals(registered, vm.currentUser.value)
        assertNull(vm.signInPresentation.value)
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
    fun `GIVEN automatic sign-in is unavailable WHEN retrying THEN prior state is preserved until activity starts`() = runTest {
        val registered = User.RegisteredUser(UserId("2"), "Test User", "test@example.com")
        val vm = viewModel(
            userRepo = ScriptedSignInUserRepository(
                SignInResult.Failure(SignInFailureReason.SERVICE_UNAVAILABLE),
                SignInResult.Success(registered),
            ),
        )
        val previousState = vm.uiState.value

        vm.startActivity(context)

        assertSame(previousState, vm.uiState.value)
        assertNotNull(vm.signInPresentation.value).onRetry()
        runCurrent()

        assertEquals(registered, vm.currentUser.value)
        assertOngoing(vm)
        assertNull(vm.signInPresentation.value)
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
    fun `GIVEN no local GPS fix WHEN a participant snapshot arrives THEN markers refresh immediately`() = runTest {
        val activityRepo = activityRepo(TestFixtures.ongoingActivity)
        val initialLocations = TestFixtures.ongoingActivityLocations
        val refreshedLocations = initialLocations.dropLast(1)
        val locationSnapshots = MutableStateFlow(initialLocations)
        val locationRepo = mockk<LocationRepository>()
        coEvery { locationRepo.getForActivity(TestFixtures.ONGOING_ACTIVITY_ID) } returns initialLocations
        every { locationRepo.observeForActivity(TestFixtures.ONGOING_ACTIVITY_ID) } returns locationSnapshots
        coEvery { locationRepo.reconcileObservation(any()) } returns Unit
        val userRepo = userRepo().also { it.signIn(context) }
        val vm = viewModel(
            activityRepo = activityRepo,
            userRepo = userRepo,
            locationRepo = locationRepo,
        )
        vm.signIn(context)
        runCurrent()
        vm.triggerOngoingState(TestFixtures.ONGOING_ACTIVITY_ID, context)
        assertEquals(initialLocations, vm.participantLocations.value)

        vm.startParticipantLocationMonitoring(refreshIntervalMillis = 1_000L)
        try {
            runCurrent()
            locationSnapshots.value = refreshedLocations
            runCurrent()
            assertEquals(refreshedLocations, vm.participantLocations.value)

            advanceTimeBy(999L)
            runCurrent()
            assertEquals(refreshedLocations, vm.participantLocations.value)

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
            coVerify(exactly = 1) {
                locationRepo.getForActivity(TestFixtures.ONGOING_ACTIVITY_ID)
            }
        } finally {
            vm.stopParticipantLocationMonitoring()
        }
    }

    @Test
    fun `GIVEN raw locations WHEN refreshing THEN only organizer and current participants remain`() = runTest {
        val organizerId = TestFixtures.ORGANIZER_ID
        val retainedParticipantId = TestFixtures.PARTICIPANT_1_ID
        val departingParticipantId = TestFixtures.PARTICIPANT_2_ID
        val unrelatedId = UserId("unrelated-user")
        val activity = TestFixtures.ongoingActivity.copy(
            participantIds = listOf(retainedParticipantId, departingParticipantId),
        )
        val activityRepo = activityRepo(activity)
        val rawLocations = listOf(
            participantLocation(organizerId),
            participantLocation(retainedParticipantId),
            participantLocation(departingParticipantId),
            participantLocation(unrelatedId),
        )
        val locationRepo = ControlledParticipantLocationRepository { _, _ -> rawLocations }
        val vm = viewModel(activityRepo = activityRepo, locationRepo = locationRepo)

        vm.triggerOngoingState(activity.id, context)
        assertEquals(
            setOf(organizerId, retainedParticipantId, departingParticipantId),
            vm.participantLocations.value.mapTo(mutableSetOf()) { it.userId },
        )
        assertEquals(
            vm.participantLocations.value,
            assertIs<MapUiState.Ongoing>(vm.uiState.value).locations,
        )

        vm.startParticipantLocationMonitoring(refreshIntervalMillis = 1_000L)
        try {
            advanceTimeBy(1_001L)
            runCurrent()
            assertEquals(
                setOf(organizerId, retainedParticipantId, departingParticipantId),
                vm.participantLocations.value.mapTo(mutableSetOf()) { it.userId },
            )

            activityRepo.removeParticipant(activity.id, departingParticipantId)
            advanceTimeBy(1_001L)
            runCurrent()

            assertEquals(
                setOf(organizerId, retainedParticipantId),
                vm.participantLocations.value.mapTo(mutableSetOf()) { it.userId },
            )
            val ongoing = assertIs<MapUiState.Ongoing>(vm.uiState.value)
            assertEquals(vm.participantLocations.value, ongoing.locations)
            assertEquals(listOf(retainedParticipantId), ongoing.participantIds)
            assertEquals(
                listOf(
                    activity.locationObservationScope(),
                    activity.locationObservationScope(),
                    activity.copy(participantIds = listOf(retainedParticipantId)).locationObservationScope(),
                ),
                locationRepo.reconciledScopes,
            )
            assertEquals(1, locationRepo.readCount.get())
        } finally {
            vm.stopParticipantLocationMonitoring()
        }
    }

    @Test
    fun `GIVEN participant monitoring is already active WHEN started again THEN only one refresh loop runs`() = runTest {
        val initialLocations = TestFixtures.ongoingActivityLocations
        val locationRepo = mockk<LocationRepository>()
        coEvery { locationRepo.getForActivity(TestFixtures.ONGOING_ACTIVITY_ID) } returns initialLocations
        every { locationRepo.observeForActivity(TestFixtures.ONGOING_ACTIVITY_ID) } returns
            MutableStateFlow(initialLocations)
        coEvery { locationRepo.reconcileObservation(any()) } returns Unit
        val vm = viewModel(
            activityRepo = activityRepo(TestFixtures.ongoingActivity),
            locationRepo = locationRepo,
        )
        vm.triggerOngoingState(TestFixtures.ONGOING_ACTIVITY_ID, context)

        vm.startParticipantLocationMonitoring(refreshIntervalMillis = 1_000L)
        vm.startParticipantLocationMonitoring(refreshIntervalMillis = 1_000L)
        try {
            advanceTimeBy(1_001L)
            runCurrent()

            coVerify(exactly = 1) {
                locationRepo.getForActivity(TestFixtures.ONGOING_ACTIVITY_ID)
            }
            coVerify(exactly = 2) { locationRepo.reconcileObservation(any()) }
        } finally {
            vm.stopParticipantLocationMonitoring()
        }
    }

    @Test
    fun `GIVEN participant monitoring is stopped WHEN refresh deadlines pass THEN no more locations are read`() = runTest {
        val locationRepo = mockk<LocationRepository>()
        coEvery { locationRepo.getForActivity(TestFixtures.ONGOING_ACTIVITY_ID) } returns
            TestFixtures.ongoingActivityLocations
        every { locationRepo.observeForActivity(TestFixtures.ONGOING_ACTIVITY_ID) } returns
            MutableStateFlow(TestFixtures.ongoingActivityLocations)
        coEvery { locationRepo.reconcileObservation(any()) } returns Unit
        val vm = viewModel(
            activityRepo = activityRepo(TestFixtures.ongoingActivity),
            locationRepo = locationRepo,
        )
        vm.triggerOngoingState(TestFixtures.ONGOING_ACTIVITY_ID, context)
        vm.startParticipantLocationMonitoring(refreshIntervalMillis = 1_000L)
        vm.stopParticipantLocationMonitoring()

        advanceTimeBy(2_001L)
        runCurrent()

        coVerify(exactly = 1) {
            locationRepo.getForActivity(TestFixtures.ONGOING_ACTIVITY_ID)
        }
        coVerify(exactly = 1) { locationRepo.reconcileObservation(any()) }
    }

    @Test
    fun `GIVEN there is no ongoing activity WHEN refresh deadlines pass THEN no locations are read`() = runTest {
        val locationRepo = ControlledParticipantLocationRepository { _, _ -> emptyList() }
        val vm = viewModel(locationRepo = locationRepo)

        vm.startParticipantLocationMonitoring(refreshIntervalMillis = 1_000L)
        try {
            advanceTimeBy(2_001L)
            runCurrent()

            assertEquals(0, locationRepo.readCount.get())
        } finally {
            vm.stopParticipantLocationMonitoring()
        }
    }

    @Test
    fun `GIVEN a participant refresh is in flight WHEN activity becomes idle THEN stale locations are not restored`() = runTest {
        val initialLocations = TestFixtures.ongoingActivityLocations
        val staleLocations = initialLocations.dropLast(1)
        val refreshStarted = CompletableDeferred<Unit>()
        val releaseRefresh = CompletableDeferred<Unit>()
        val locationRepo = mockk<LocationRepository>()
        coEvery { locationRepo.getForActivity(TestFixtures.ONGOING_ACTIVITY_ID) } returns initialLocations
        coEvery { locationRepo.reconcileObservation(any()) } returns Unit
        every { locationRepo.observeForActivity(TestFixtures.ONGOING_ACTIVITY_ID) } returns flow {
            refreshStarted.complete(Unit)
            releaseRefresh.await()
            emit(staleLocations)
        }
        val vm = viewModel(
            activityRepo = activityRepo(TestFixtures.ongoingActivity),
            locationRepo = locationRepo,
        )
        vm.triggerOngoingState(TestFixtures.ONGOING_ACTIVITY_ID, context)
        vm.startParticipantLocationMonitoring(refreshIntervalMillis = 1_000L)
        try {
            runCurrent()
            refreshStarted.await()

            vm.triggerIdleState()
            releaseRefresh.complete(Unit)
            runCurrent()

            assertTrue(vm.participantLocations.value.isEmpty())
        } finally {
            vm.stopParticipantLocationMonitoring()
        }
    }

    @Test
    fun `GIVEN observation switches activities WHEN the old source emits THEN stale markers are not restored`() = runTest {
        val activityA = TestFixtures.ongoingActivity
        val activityB = activityA.copy(id = IturActivityId("otherActivity0000001"))
        val locationsA = TestFixtures.ongoingActivityLocations
        val locationsB = locationsA.map { it.copy(activityId = activityB.id) }
        val oldSnapshots = MutableSharedFlow<List<ParticipantLocation>>(extraBufferCapacity = 1)
        val currentSnapshots = MutableStateFlow(locationsB)
        val locationRepo = mockk<LocationRepository>()
        coEvery { locationRepo.reconcileObservation(any()) } returns Unit
        coEvery { locationRepo.getForActivity(activityA.id) } returns locationsA
        coEvery { locationRepo.getForActivity(activityB.id) } returns locationsB
        every { locationRepo.observeForActivity(activityA.id) } returns oldSnapshots
        every { locationRepo.observeForActivity(activityB.id) } returns currentSnapshots
        val vm = viewModel(
            activityRepo = activityRepo(activityA, activityB),
            locationRepo = locationRepo,
        )

        vm.triggerOngoingState(activityA.id, context)
        vm.startParticipantLocationMonitoring(refreshIntervalMillis = 1_000L)
        runCurrent()
        vm.triggerOngoingState(activityB.id, context)
        runCurrent()

        oldSnapshots.tryEmit(locationsA.dropLast(1))
        runCurrent()

        assertEquals(activityB.id, assertIs<MapUiState.Ongoing>(vm.uiState.value).activity.id)
        assertEquals(locationsB, vm.participantLocations.value)
        coVerify(exactly = 1) { locationRepo.getForActivity(activityA.id) }
        coVerify(exactly = 1) { locationRepo.getForActivity(activityB.id) }
        vm.stopParticipantLocationMonitoring()
    }

    @Test
    fun `GIVEN an accepted GPS callback WHEN reporting succeeds THEN timing spans queue normalization and repository`() = runTest {
        val measurement = RecordingLocationReportMeasurement()
        val updateCompleted = CompletableDeferred<Unit>()
        val locationRepo = mockk<LocationRepository>()
        every { locationRepo.newReportMeasurement() } returns measurement
        coEvery { locationRepo.getForActivity(TestFixtures.ONGOING_ACTIVITY_ID) } returns emptyList()
        coEvery { locationRepo.reconcileObservation(any()) } returns Unit
        coEvery {
            locationRepo.updateForParticipant(
                TestFixtures.ORGANIZER_ID,
                TestFixtures.ONGOING_ACTIVITY_ID,
                any(),
                measurement,
            )
        } answers {
            updateCompleted.complete(Unit)
        }
        val callback = slot<(android.location.Location) -> Unit>()
        every { locationClient.requestUpdates(any(), capture(callback)) } just Runs
        val userRepo = userRepo().also { it.signIn(context) }
        val vm = viewModel(
            activityRepo = activityRepo(TestFixtures.ongoingActivity),
            userRepo = userRepo,
            locationRepo = locationRepo,
        )
        vm.triggerOngoingState(TestFixtures.ONGOING_ACTIVITY_ID, context)
        vm.onLocationPermissionChanged(granted = true)
        val location = mockk<android.location.Location>(relaxed = true)
        every { location.latitude } returns 41.3874
        every { location.longitude } returns 2.1686

        callback.captured(location)

        assertEquals("start:${LocationReportStage.CALLBACK_QUEUE}", measurement.events.first())
        withContext(Dispatchers.Default) {
            withTimeout(5_000L) { updateCompleted.await() }
            withTimeout(5_000L) { measurement.completed.await() }
        }
        assertEquals(
            listOf(
                "start:${LocationReportStage.CALLBACK_QUEUE}",
                "finish:${LocationReportStage.CALLBACK_QUEUE}",
                "start:${LocationReportStage.NORMALIZATION}",
                "finish:${LocationReportStage.NORMALIZATION}",
                "complete:${LocationReportOutcome.SUCCESS}",
            ),
            measurement.events,
        )
        coVerify(exactly = 1) {
            locationRepo.updateForParticipant(
                TestFixtures.ORGANIZER_ID,
                TestFixtures.ONGOING_ACTIVITY_ID,
                any(),
                measurement,
            )
        }
        coVerify(exactly = 1) {
            locationRepo.getForActivity(TestFixtures.ONGOING_ACTIVITY_ID)
        }
    }

    @Test
    fun `GIVEN repository cancellation WHEN reporting a GPS callback THEN measurement is cancelled`() = runTest {
        val measurement = RecordingLocationReportMeasurement()
        val locationRepo = mockk<LocationRepository>()
        every { locationRepo.newReportMeasurement() } returns measurement
        coEvery { locationRepo.getForActivity(TestFixtures.ONGOING_ACTIVITY_ID) } returns emptyList()
        coEvery { locationRepo.reconcileObservation(any()) } returns Unit
        coEvery {
            locationRepo.updateForParticipant(
                TestFixtures.ORGANIZER_ID,
                TestFixtures.ONGOING_ACTIVITY_ID,
                any(),
                measurement,
            )
        } throws kotlinx.coroutines.CancellationException("superseded work")
        val callback = slot<(android.location.Location) -> Unit>()
        every { locationClient.requestUpdates(any(), capture(callback)) } just Runs
        val userRepo = userRepo().also { it.signIn(context) }
        val vm = viewModel(
            activityRepo = activityRepo(TestFixtures.ongoingActivity),
            userRepo = userRepo,
            locationRepo = locationRepo,
        )
        vm.signIn(context)
        runCurrent()
        vm.triggerOngoingState(TestFixtures.ONGOING_ACTIVITY_ID, context)
        vm.onLocationPermissionChanged(granted = true)

        callback.captured(mockk(relaxed = true))

        withContext(Dispatchers.Default) {
            withTimeout(5_000L) { measurement.completed.await() }
        }
        assertEquals(LocationReportOutcome.CANCELLED, measurement.outcome)
    }

    @Test
    fun `GIVEN an in-flight location report WHEN newer fixes arrive THEN only the latest pending fix is written`() = runTest {
        val allMeasurements = List(3) { RecordingLocationReportMeasurement() }
        val measurements = ArrayDeque(allMeasurements)
        val firstWriteEntered = CompletableDeferred<Unit>()
        val releaseFirstWrite = CompletableDeferred<Unit>()
        val activeWrites = AtomicInteger()
        val maximumActiveWrites = AtomicInteger()
        val writtenLongitudes = ConcurrentLinkedQueue<Double>()
        val locationRepo = ControlledLocationRepository(measurements) {
            val active = activeWrites.incrementAndGet()
            maximumActiveWrites.updateAndGet { maxOf(it, active) }
            try {
                writtenLongitudes += it.longitude
                if (writtenLongitudes.size == 1) {
                    firstWriteEntered.complete(Unit)
                    releaseFirstWrite.await()
                }
            } finally {
                activeWrites.decrementAndGet()
            }
        }
        val callback = slot<(android.location.Location) -> Unit>()
        every { locationClient.requestUpdates(any(), capture(callback)) } just Runs
        val userRepo = userRepo().also { it.signIn(context) }
        val vm = viewModel(
            activityRepo = activityRepo(TestFixtures.ongoingActivity),
            userRepo = userRepo,
            locationRepo = locationRepo,
        )
        vm.triggerOngoingState(TestFixtures.ONGOING_ACTIVITY_ID, context)
        vm.onLocationPermissionChanged(granted = true)

        callback.captured(locationFix(2.16))
        withContext(Dispatchers.Default) { withTimeout(5_000L) { firstWriteEntered.await() } }
        callback.captured(locationFix(2.17))
        callback.captured(locationFix(2.18))
        releaseFirstWrite.complete(Unit)
        withContext(Dispatchers.Default) { withTimeout(5_000L) { allMeasurements[2].completed.await() } }

        assertEquals(listOf(2.16, 2.18), writtenLongitudes.toList())
        assertEquals(1, maximumActiveWrites.get())
        assertEquals(LocationReportOutcome.SUCCESS, allMeasurements[0].outcome)
        assertEquals(LocationReportOutcome.SUPERSEDED, allMeasurements[1].outcome)
        assertEquals(LocationReportOutcome.SUCCESS, allMeasurements[2].outcome)
    }

    @Test
    fun `GIVEN a failed location report WHEN a newer fix is pending THEN the writer continues`() = runTest {
        val allMeasurements = List(2) { RecordingLocationReportMeasurement() }
        val measurements = ArrayDeque(allMeasurements)
        val firstWriteEntered = CompletableDeferred<Unit>()
        val releaseFirstWrite = CompletableDeferred<Unit>()
        val activeWrites = AtomicInteger()
        val maximumActiveWrites = AtomicInteger()
        val attemptedLongitudes = ConcurrentLinkedQueue<Double>()
        val locationRepo = ControlledLocationRepository(measurements) {
            val active = activeWrites.incrementAndGet()
            maximumActiveWrites.updateAndGet { maximum -> maxOf(maximum, active) }
            try {
                attemptedLongitudes += it.longitude
                if (attemptedLongitudes.size == 1) {
                    firstWriteEntered.complete(Unit)
                    releaseFirstWrite.await()
                    error("first report failed")
                }
            } finally {
                activeWrites.decrementAndGet()
            }
        }
        val callback = slot<(android.location.Location) -> Unit>()
        every { locationClient.requestUpdates(any(), capture(callback)) } just Runs
        val userRepo = userRepo().also { it.signIn(context) }
        val vm = viewModel(
            activityRepo = activityRepo(TestFixtures.ongoingActivity),
            userRepo = userRepo,
            locationRepo = locationRepo,
        )
        vm.triggerOngoingState(TestFixtures.ONGOING_ACTIVITY_ID, context)
        vm.onLocationPermissionChanged(granted = true)

        callback.captured(locationFix(2.16))
        withContext(Dispatchers.Default) { withTimeout(5_000L) { firstWriteEntered.await() } }
        callback.captured(locationFix(2.18))
        releaseFirstWrite.complete(Unit)
        withContext(Dispatchers.Default) { withTimeout(5_000L) { allMeasurements[1].completed.await() } }

        assertEquals(listOf(2.16, 2.18), attemptedLongitudes.toList())
        assertEquals(1, maximumActiveWrites.get())
        assertEquals(LocationReportOutcome.FAILED, allMeasurements[0].outcome)
        assertEquals(LocationReportOutcome.SUCCESS, allMeasurements[1].outcome)
    }

    @Test
    fun `GIVEN a failed location report WHEN its old session retry runs THEN it is cancelled`() = runTest {
        val allMeasurements = List(2) { RecordingLocationReportMeasurement() }
        val measurements = ArrayDeque(allMeasurements)
        val locationRepo = ControlledLocationRepository(measurements) {
            error("report failed")
        }
        val callback = slot<(android.location.Location) -> Unit>()
        every { locationClient.requestUpdates(any(), capture(callback)) } just Runs
        val userRepo = userRepo().also { it.signIn(context) }
        val vm = viewModel(
            activityRepo = activityRepo(TestFixtures.ongoingActivity),
            userRepo = userRepo,
            locationRepo = locationRepo,
        )
        vm.triggerOngoingState(TestFixtures.ONGOING_ACTIVITY_ID, context)
        vm.onLocationPermissionChanged(granted = true)

        callback.captured(locationFix(2.16))
        withContext(Dispatchers.Default) { withTimeout(5_000L) { allMeasurements[0].completed.await() } }
        val failedState = assertIs<MapUiState.RecoverableError>(vm.uiState.value)
        vm.triggerIdleState()

        failedState.onRetry()
        withContext(Dispatchers.Default) { withTimeout(5_000L) { allMeasurements[1].completed.await() } }

        assertEquals(1, locationRepo.updateCount.get())
        assertEquals(LocationReportOutcome.FAILED, allMeasurements[0].outcome)
        assertEquals(LocationReportOutcome.CANCELLED, allMeasurements[1].outcome)
    }

    @Test
    fun `GIVEN a failed location report WHEN a newer fix succeeds THEN the old retry is superseded`() = runTest {
        val allMeasurements = List(3) { RecordingLocationReportMeasurement() }
        val measurements = ArrayDeque(allMeasurements)
        val attemptedLongitudes = ConcurrentLinkedQueue<Double>()
        val locationRepo = ControlledLocationRepository(measurements) {
            attemptedLongitudes += it.longitude
            if (attemptedLongitudes.size == 1) error("first report failed")
        }
        val callback = slot<(android.location.Location) -> Unit>()
        every { locationClient.requestUpdates(any(), capture(callback)) } just Runs
        val userRepo = userRepo().also { it.signIn(context) }
        val vm = viewModel(
            activityRepo = activityRepo(TestFixtures.ongoingActivity),
            userRepo = userRepo,
            locationRepo = locationRepo,
        )
        vm.triggerOngoingState(TestFixtures.ONGOING_ACTIVITY_ID, context)
        vm.onLocationPermissionChanged(granted = true)

        callback.captured(locationFix(2.16))
        withContext(Dispatchers.Default) { withTimeout(5_000L) { allMeasurements[0].completed.await() } }
        val failedState = assertIs<MapUiState.RecoverableError>(vm.uiState.value)
        callback.captured(locationFix(2.18))
        withContext(Dispatchers.Default) { withTimeout(5_000L) { allMeasurements[1].completed.await() } }

        failedState.onRetry()
        withContext(Dispatchers.Default) { withTimeout(5_000L) { allMeasurements[2].completed.await() } }

        assertEquals(listOf(2.16, 2.18), attemptedLongitudes.toList())
        assertEquals(2, locationRepo.updateCount.get())
        assertEquals(LocationReportOutcome.FAILED, allMeasurements[0].outcome)
        assertEquals(LocationReportOutcome.SUCCESS, allMeasurements[1].outcome)
        assertEquals(LocationReportOutcome.SUPERSEDED, allMeasurements[2].outcome)
    }

    @Test
    fun `GIVEN active and pending location reports WHEN the activity is cleared THEN both are cancelled`() = runTest {
        val allMeasurements = List(2) { RecordingLocationReportMeasurement() }
        val measurements = ArrayDeque(allMeasurements)
        val firstWriteEntered = CompletableDeferred<Unit>()
        val locationRepo = ControlledLocationRepository(measurements) {
            firstWriteEntered.complete(Unit)
            CompletableDeferred<Unit>().await()
        }
        val callback = slot<(android.location.Location) -> Unit>()
        every { locationClient.requestUpdates(any(), capture(callback)) } just Runs
        val userRepo = userRepo().also { it.signIn(context) }
        val vm = viewModel(
            activityRepo = activityRepo(TestFixtures.ongoingActivity),
            userRepo = userRepo,
            locationRepo = locationRepo,
        )
        vm.triggerOngoingState(TestFixtures.ONGOING_ACTIVITY_ID, context)
        vm.onLocationPermissionChanged(granted = true)

        callback.captured(locationFix(2.16))
        withContext(Dispatchers.Default) { withTimeout(5_000L) { firstWriteEntered.await() } }
        callback.captured(locationFix(2.17))
        vm.triggerIdleState()
        withContext(Dispatchers.Default) {
            withTimeout(5_000L) {
                allMeasurements.forEach { it.completed.await() }
            }
        }

        assertEquals(LocationReportOutcome.CANCELLED, allMeasurements[0].outcome)
        assertEquals(LocationReportOutcome.CANCELLED, allMeasurements[1].outcome)
        assertEquals(1, locationRepo.updateCount.get())
    }

    private fun locationFix(longitude: Double) = mockk<android.location.Location>(relaxed = true).also {
        every { it.latitude } returns 41.3874
        every { it.longitude } returns longitude
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
            assertFalse(vm.currentUser.value!!.id in result.data.participantIds)
            assertIs<MapUiState.Idle>(vm.uiState.value)

            // A repeated UI cleanup is a no-op after the local session has been cleared.
            vm.leaveActivity()
            assertIs<MapUiState.Idle>(vm.uiState.value)
        }
    }

    @Test
    fun `GIVEN an ongoing activity as participant WHEN leaving THEN participant is removed and uiState is Idle`() {
        runTest {
            val userRepo = userRepo()
            val signedInUser = assertIs<SignInResult.Success>(userRepo.signIn(context)).user
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
            val signedInUser = assertIs<SignInResult.Success>(userRepo.signIn(context)).user
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
            runCurrent()

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
    fun `GIVEN a persistent outage WHEN virtual time advances THEN retries use bounded exponential backoff`() = runTest {
        val firestore = FakeBackendHealthCheck("firestore", "Cloud Firestore", false)
        val vm = viewModel(healthChecks = setOf(firestore))
        vm.startBackendMonitoring()
        var expectedProbes = firestore.probeCount

        listOf(5, 10, 20, 40, 60).forEachIndexed { index, delaySeconds ->
            mainDispatcherRule.testDispatcher.scheduler.advanceTimeBy(delaySeconds * 1_000L + 1L)
            runCurrent()

            expectedProbes++
            assertEquals(expectedProbes, firestore.probeCount)
            assertEquals(
                listOf(10, 20, 40, 60, 60)[index],
                vm.backendAvailability.value.retryCountdown,
            )
        }
        vm.stopBackendMonitoring()
    }

    @Test
    fun `GIVEN monitoring is stopped WHEN resumed THEN one retry loop is scheduled`() = runTest {
        val firestore = FakeBackendHealthCheck("firestore", "Cloud Firestore", false)
        val vm = viewModel(healthChecks = setOf(firestore))
        vm.startBackendMonitoring()
        vm.stopBackendMonitoring()
        val probesWhileInactive = firestore.probeCount

        mainDispatcherRule.testDispatcher.scheduler.advanceTimeBy(60_000L)
        runCurrent()

        assertEquals(probesWhileInactive, firestore.probeCount)
        vm.startBackendMonitoring()
        val probesAfterResume = firestore.probeCount
        vm.startBackendMonitoring()
        val probesAfterRepeatedStart = firestore.probeCount

        assertEquals(probesAfterResume, probesAfterRepeatedStart)

        mainDispatcherRule.testDispatcher.scheduler.advanceTimeBy(5_001L)
        runCurrent()

        assertEquals(probesAfterRepeatedStart + 1, firestore.probeCount)
        vm.stopBackendMonitoring()
    }

    @Test
    fun `GIVEN manual retry and recovery THEN backoff resets and healthy cadence is thirty seconds`() = runTest {
        val firestore = FakeBackendHealthCheck("firestore", "Cloud Firestore", false)
        val vm = viewModel(healthChecks = setOf(firestore))
        vm.startBackendMonitoring()

        mainDispatcherRule.testDispatcher.scheduler.advanceTimeBy(5_001L)
        runCurrent()
        assertEquals(10, vm.backendAvailability.value.retryCountdown)

        vm.retryNow()
        assertEquals(5, vm.backendAvailability.value.retryCountdown)

        firestore.available = true
        vm.retryNow()
        val probesAfterRecovery = firestore.probeCount
        assertEquals(BackendAvailabilityUiState(), vm.backendAvailability.value)

        mainDispatcherRule.testDispatcher.scheduler.advanceTimeBy(29_999L)
        runCurrent()
        assertEquals(probesAfterRecovery, firestore.probeCount)
        mainDispatcherRule.testDispatcher.scheduler.advanceTimeBy(1L)
        runCurrent()
        assertEquals(probesAfterRecovery + 1, firestore.probeCount)
        vm.stopBackendMonitoring()
    }

    @Test
    fun `GIVEN one of two services recovers WHEN scheduled retry runs THEN only the remaining outage retries`() = runTest {
        val auth = FakeBackendHealthCheck("auth", "Firebase Authentication", false)
        val firestore = FakeBackendHealthCheck("firestore", "Cloud Firestore", false)
        val vm = viewModel(healthChecks = setOf(auth, firestore))
        vm.startBackendMonitoring()
        auth.available = true

        mainDispatcherRule.testDispatcher.scheduler.advanceTimeBy(5_001L)
        runCurrent()

        assertEquals(listOf(firestore.service), vm.backendAvailability.value.failingServices)
        assertEquals(10, vm.backendAvailability.value.retryCountdown)
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
        val participantActivity = TestFixtures.ongoingActivity.copy(
            organizerId = UserId("other-organizer"),
            participantIds = listOf(TestFixtures.ORGANIZER_ID),
        )
        val activityRepo = mockk<ActivityRepository>(
            moreInterfaces = arrayOf(ParticipantSignalRepository::class),
        )
        coEvery { activityRepo.getActivities(any()) } returns DataResult.Success(emptyList())
        coEvery { activityRepo.getActiveActivityId(TestFixtures.ORGANIZER_ID) } returns DataResult.Success(null)
        coEvery {
            activityRepo.addParticipant(
                TestFixtures.ONGOING_ACTIVITY_ID,
                TestFixtures.ORGANIZER_ID,
            )
        } returns DataResult.Success(participantActivity)
        coEvery { activityRepo.getActivity(TestFixtures.ONGOING_ACTIVITY_ID) } returns
            DataResult.Success(participantActivity)
        coEvery {
            (activityRepo as ParticipantSignalRepository).setParticipantSignal(
                TestFixtures.ONGOING_ACTIVITY_ID,
                TestFixtures.ORGANIZER_ID,
                ParticipantSignal.NEEDS_HELP,
            )
        } throws IllegalStateException("offline")
        val userRepo = userRepo().also { it.signIn(context) }
        val vm = viewModel(
            activityRepo = activityRepo,
            userRepo = userRepo,
            healthChecks = setOf(firestore),
        )
        vm.joinActivity(TestFixtures.ONGOING_ACTIVITY_ID, context)
        assertIs<MapUiState.Ongoing>(vm.uiState.value)

        vm.requestAttention()
        runCurrent()

        assertEquals(listOf(firestore.service), vm.backendAvailability.value.failingServices)
        assertIs<MapUiState.RecoverableError>(vm.uiState.value)
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
    fun `GIVEN organizer profile access is denied WHEN restoring an activity THEN ongoing state uses an ID-only organizer`() = runTest {
        val userRepo = mockk<UserRepository>(relaxed = true)
        coEvery { userRepo.getCurrentUser() } returns User.AnonymousUser(UserId("participant"))
        coEvery { userRepo.getAll(any()) } throws IllegalStateException("profile access denied")
        val activityRepo = activityRepo(TestFixtures.ongoingActivity)
        val vm = viewModel(activityRepo = activityRepo, userRepo = userRepo)

        vm.triggerOngoingState(TestFixtures.ONGOING_ACTIVITY_ID, context)

        val state = assertIs<MapUiState.Ongoing>(vm.uiState.value)
        val organizer = assertIs<User.AnonymousUser>(state.organizer)
        assertEquals(TestFixtures.ORGANIZER_ID, organizer.id)
    }

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
            coEvery { activityRepo.getActiveActivityId(any()) } returns DataResult.Success(null)
            coEvery { activityRepo.createActivity(TestFixtures.ORGANIZER_ID) } returns DataResult.Error("quota exceeded")

            val vm = viewModel(activityRepo = activityRepo)
            runCurrent()
            vm.startActivity(context)
            runCurrent()

            val state = assertIs<MapUiState.Error>(vm.uiState.value)
            assertEquals("quota exceeded", state.message)
        }
    }
}

private class RecordingLocationReportMeasurement : LocationReportMeasurement {
    private val mutableEvents = mutableListOf<String>()
    val completed = CompletableDeferred<Unit>()
    var outcome: LocationReportOutcome? = null
        private set

    val events: List<String>
        get() = synchronized(mutableEvents) { mutableEvents.toList() }

    override fun stageStarted(stage: LocationReportStage) {
        synchronized(mutableEvents) { mutableEvents += "start:$stage" }
    }

    override fun stageFinished(stage: LocationReportStage) {
        synchronized(mutableEvents) { mutableEvents += "finish:$stage" }
    }

    override fun complete(outcome: LocationReportOutcome) {
        synchronized(mutableEvents) {
            this.outcome = outcome
            mutableEvents += "complete:$outcome"
        }
        completed.complete(Unit)
    }
}

private class ControlledLocationRepository(
    private val measurements: ArrayDeque<RecordingLocationReportMeasurement>,
    private val onUpdate: suspend (Location) -> Unit,
) : LocationRepository {
    val updateCount = AtomicInteger()
    override fun newReportMeasurement(): LocationReportMeasurement = measurements.removeFirst()
    override suspend fun getForActivity(
        activityId: IturActivityId,
    ) = emptyList<cat.itur.app.core.model.ParticipantLocation>()

    override suspend fun updateForParticipant(
        userId: UserId,
        activityId: IturActivityId,
        location: Location,
        measurement: LocationReportMeasurement,
    ) {
        updateCount.incrementAndGet()
        onUpdate(location)
    }
    override suspend fun removeForActivity(activityId: IturActivityId) = Unit
    override suspend fun removeForParticipant(userId: UserId, activityId: IturActivityId) = Unit
}

private class ControlledParticipantLocationRepository(
    private val onRead: suspend (IturActivityId, Int) -> List<cat.itur.app.core.model.ParticipantLocation>,
) : LocationRepository {
    val readCount = AtomicInteger()
    val reconciledScopes = mutableListOf<LocationObservationScope>()

    override suspend fun getForActivity(activityId: IturActivityId) = onRead(activityId, readCount.getAndIncrement())

    override suspend fun reconcileObservation(scope: LocationObservationScope) {
        reconciledScopes += scope
    }

    override suspend fun updateForParticipant(
        userId: UserId,
        activityId: IturActivityId,
        location: Location,
        measurement: LocationReportMeasurement,
    ) = Unit

    override suspend fun removeForActivity(activityId: IturActivityId) = Unit
    override suspend fun removeForParticipant(userId: UserId, activityId: IturActivityId) = Unit
}

private fun participantLocation(userId: UserId) = ParticipantLocation(
    activityId = TestFixtures.ONGOING_ACTIVITY_ID,
    userId = userId,
    userName = "User ${userId.value}",
    location = Location(latitude = 51.0, longitude = 0.0),
)

private class ScriptedSignInUserRepository(
    vararg results: SignInResult,
) : UserRepository {
    private val results = ArrayDeque(results.toList())
    private var current: User = User.AnonymousUser(UserId("1"))

    override suspend fun getCurrentUser(): User = current

    override suspend fun getAll(ids: List<UserId>): List<User> = listOf(current).filter { it.id in ids }

    override suspend fun signIn(context: Context): SignInResult = results.removeFirst().also {
        if (it is SignInResult.Success) current = it.user
    }

    override suspend fun signOut() {
        current = User.AnonymousUser(UserId("1"))
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
