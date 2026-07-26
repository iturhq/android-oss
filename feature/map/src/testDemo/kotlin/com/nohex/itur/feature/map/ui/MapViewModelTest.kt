/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.feature.map.ui

import android.content.Context
import android.os.Looper
import com.nohex.itur.core.data.TestFixtures
import com.nohex.itur.core.data.repository.ActivityRepository
import com.nohex.itur.core.data.repository.DataResult
import com.nohex.itur.core.data.repository.FakeActivityRepository
import com.nohex.itur.core.data.repository.FakeLocationRepository
import com.nohex.itur.core.data.repository.FakeUserRepository
import com.nohex.itur.core.data.repository.UserRepository
import com.nohex.itur.core.domain.id.IturActivityId
import com.nohex.itur.core.domain.id.UserId
import com.nohex.itur.core.domain.model.User
import com.nohex.itur.core.location.LocationClient
import com.nohex.itur.core.model.Broadcast
import com.nohex.itur.core.model.IturActivity
import com.nohex.itur.core.model.IturActivityStatus
import com.nohex.itur.feature.map.config.MapStyleConfig
import com.nohex.itur.feature.map.notifications.BroadcastNotifier
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail

// An activity where the organiser is a user that is NOT in FakeUserRepository's registered user
// (UserId("2")), so that the signed-in user is treated as a participant, not the organiser.
private val PARTICIPANT_ACTIVITY = TestFixtures.ongoingActivity.copy(
    id = IturActivityId("participantActv00001"),
    organizerId = UserId("other-organizer"),
    participantIds = emptyList(),
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
    ) = MapViewModel(
        activityRepository = activityRepo,
        userRepository = userRepo,
        locationsRepository = locationRepo(activityRepo),
        locationClient = locationClient,
        broadcastNotifier = broadcastNotifier,
        mapStyleConfig = MapStyleConfig(styleUrl = "https://example.invalid/style.json"),
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

    // --- leaveActivity ---

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

    // --- initialize / retry backoff ---

    @Test
    fun `GIVEN the backend is unreachable WHEN the ViewModel is created THEN uiState becomes BackendUnavailable with the first backoff countdown`() {
        runTest {
            val activityRepo = mockk<ActivityRepository>()
            // Bounded (recovers on the 2nd attempt): an unbounded `throws` here would leave
            // MapViewModel's own infinite-retry-until-reachable loop running against a
            // never-advanced TestDispatcher scheduler for the rest of this test's lifetime.
            coEvery { activityRepo.getActivities(any()) } throws RuntimeException("offline") andThen
                DataResult.Success(emptyList())

            val vm = viewModel(activityRepo = activityRepo)

            assertEquals(MapUiState.BackendUnavailable(countdown = 5), vm.uiState.value)

            // Drain the pending retry so no coroutine is left running past this test.
            mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
        }
    }

    @Test
    fun `GIVEN the backend recovers WHEN retryNow is called THEN uiState becomes Idle without waiting for the countdown`() {
        runTest {
            val activityRepo = mockk<ActivityRepository>()
            coEvery { activityRepo.getActivities(any()) } throws RuntimeException("offline") andThen
                DataResult.Success(emptyList())

            val vm = viewModel(activityRepo = activityRepo)
            assertIs<MapUiState.BackendUnavailable>(vm.uiState.value)

            vm.retryNow()

            assertIs<MapUiState.Idle>(vm.uiState.value)
        }
    }

    @Test
    fun `GIVEN the backend recovers during the first backoff wait WHEN the countdown elapses THEN it retries automatically and uiState becomes Idle`() {
        runTest {
            val activityRepo = mockk<ActivityRepository>()
            coEvery { activityRepo.getActivities(any()) } throws RuntimeException("offline") andThen
                DataResult.Success(emptyList())

            val vm = viewModel(activityRepo = activityRepo)
            assertEquals(MapUiState.BackendUnavailable(countdown = 5), vm.uiState.value)

            mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

            assertIs<MapUiState.Idle>(vm.uiState.value)
        }
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
            coEvery { activityRepo.createActivity(TestFixtures.ORGANIZER_ID) } returns DataResult.Error("quota exceeded")

            val vm = viewModel(activityRepo = activityRepo)
            vm.startActivity(context)

            val state = assertIs<MapUiState.Error>(vm.uiState.value)
            assertEquals("quota exceeded", state.message)
        }
    }
}
