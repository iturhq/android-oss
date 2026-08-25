/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cat.itur.app.core.data.health.BackendHealthCheck
import cat.itur.app.core.data.health.BackendHealthStatus
import cat.itur.app.core.data.health.BackendService
import cat.itur.app.core.data.repository.ActivityFilter
import cat.itur.app.core.data.repository.ActivityRepository
import cat.itur.app.core.data.repository.DataResult
import cat.itur.app.core.data.repository.LocationRepository
import cat.itur.app.core.data.repository.SignInFailureReason
import cat.itur.app.core.data.repository.SignInResult
import cat.itur.app.core.data.repository.UserRepository
import cat.itur.app.core.domain.id.IturActivityId
import cat.itur.app.core.domain.id.UserId
import cat.itur.app.core.domain.model.User
import cat.itur.app.core.domain.model.User.AnonymousUser
import cat.itur.app.core.location.LocationClient
import cat.itur.app.core.model.Broadcast
import cat.itur.app.core.model.IturActivity
import cat.itur.app.core.model.IturActivityStatus
import cat.itur.app.core.model.ParticipantLocation
import cat.itur.app.feature.map.config.LocationUpdateConfig
import cat.itur.app.feature.map.config.MapStyleConfig
import cat.itur.app.feature.map.health.BackendHealthRecoveryCoordinator
import cat.itur.app.feature.map.health.MapStyleRendererHealthReporter
import cat.itur.app.feature.map.notifications.BroadcastNotifier
import cat.itur.app.feature.map.ui.MapUiState.Ongoing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject
import cat.itur.app.core.model.Location as IturLocation

@HiltViewModel
class MapViewModel @Inject
constructor(
    private val activityRepository: ActivityRepository,
    private val userRepository: UserRepository,
    private val locationsRepository: LocationRepository,
    private val locationClient: LocationClient,
    private val broadcastNotifier: BroadcastNotifier,
    val mapStyleConfig: MapStyleConfig,
    private val locationUpdateConfig: LocationUpdateConfig,
    backendHealthChecks: Set<@JvmSuppressWildcards BackendHealthCheck>,
    private val backendHealthCoordinator: BackendHealthRecoveryCoordinator =
        BackendHealthRecoveryCoordinator(backendHealthChecks),
    private val mapStyleRendererHealthReporter: MapStyleRendererHealthReporter =
        MapStyleRendererHealthReporter(dagger.Lazy { backendHealthCoordinator }),
) : ViewModel() {
    private val _uiState = MutableStateFlow<MapUiState>(MapUiState.Idle())
    val uiState = _uiState.asStateFlow()

    // The current user.
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser = _currentUser.asStateFlow()

    private val _signInPresentation = MutableStateFlow<SignInFailurePresentation?>(null)
    val signInPresentation = _signInPresentation.asStateFlow()

    // The current activity.
    private val _ongoingActivityId = MutableStateFlow<IturActivityId?>(null)
    val ongoingActivityId: StateFlow<IturActivityId?> = _ongoingActivityId.asStateFlow()

    // The current activity's organiser ID.
    private val _organizerId = MutableStateFlow<UserId?>(null)
    val organizerId = MutableStateFlow<UserId?>(null)

    // A MapLibre feature collection representing the participants' locations.
    private val _participantLocations =
        MutableStateFlow<List<ParticipantLocation>>(mutableListOf<ParticipantLocation>())
    val participantLocations: StateFlow<List<ParticipantLocation>> =
        _participantLocations.asStateFlow()

    // The last know location, as collected by the location service.
    private val _lastLocation = MutableLiveData<Location?>()
    val lastLocation: LiveData<Location?> = _lastLocation

    private val _backendAvailability = MutableStateFlow(BackendAvailabilityUiState())
    val backendAvailability = _backendAvailability.asStateFlow()
    private var participantLocationMonitoringJob: Job? = null
    private var initialRestorePending = true
    private var initialRestoreRunning = false
    private var locationUpdatesActive = false

    fun reportMapStyleLoadFailed() = mapStyleRendererHealthReporter.styleLoadFailed()

    fun reportMapStyleLoadSucceeded() = mapStyleRendererHealthReporter.styleLoadSucceeded()

    // The most recent operator broadcast (UC-ACTIVITY-007), for an in-app banner alongside
    // the system notification posted by [broadcastNotifier]. Polling itself is driven by the UI
    // (a Compose LaunchedEffect tied to the ongoing activity, see MapScreen), not by the
    // ViewModel, so it naturally stops when the screen isn't showing an ongoing activity and
    // doesn't require an unbounded viewModelScope loop that's awkward to unit test.
    private val _latestBroadcast = MutableStateFlow<Broadcast?>(null)
    val latestBroadcast: StateFlow<Broadcast?> = _latestBroadcast.asStateFlow()
    private var lastBroadcastSeen: Date? = null

    init {
        viewModelScope.launch {
            combine(
                backendHealthCoordinator.services,
                backendHealthCoordinator.retryCountdown,
                backendHealthCoordinator.checkGeneration,
            ) { services, countdown, generation ->
                BackendAvailabilityUiState(
                    failingServices = if (generation == 0L) {
                        emptyList()
                    } else {
                        services
                            .filter { it.status != BackendHealthStatus.WORKING }
                            .map { BackendService(it.id, it.name) }
                    },
                    retryCountdown = countdown,
                )
            }.collect { availability ->
                _backendAvailability.value = availability
            }
        }
        viewModelScope.launch {
            backendHealthCoordinator.checkGeneration.collect { generation ->
                if (
                    generation > 0L &&
                    initialRestorePending &&
                    !initialRestoreRunning
                ) {
                    initialRestoreRunning = true
                    try {
                        restoreInitialState()
                    } finally {
                        initialRestoreRunning = false
                    }
                }
            }
        }
        backendHealthCoordinator.checkOnce(viewModelScope)
    }

    /**
     * Restores any in-progress state after all required services are known to be healthy.
     */
    private suspend fun restoreInitialState() {
        try {
            _currentUser.value = userRepository.getCurrentUser()
            _ongoingActivityId.value = findOngoingActivity()?.id

            initialRestorePending = false
            _uiState.value = MapUiState.Idle()
        } catch (e: Exception) {
            Log.e("MapViewModel", "Backend unavailable: ${e.message}", e)
            reportBackendFailure(e, assumeAllServices = true)
        }
    }

    private suspend fun findOngoingActivity(): IturActivity? {
        val user = _currentUser.value ?: return null

        // Current admission writes the caller's canonical active reservation and participant
        // membership to separate documents, leaving the legacy participantIds array frozen.
        // Resolve that exact activity first; the queries below remain as compatibility fallback
        // for pre-reservation installs and legacy activity records.
        val reservedActivityId = when (val result = activityRepository.getActiveActivityId(user.id)) {
            is DataResult.Success -> result.data
            is DataResult.Error -> throw BackendInitializationException(result.message)
            is DataResult.NotFound -> null
        }
        if (reservedActivityId != null) {
            when (val result = activityRepository.getActivity(reservedActivityId)) {
                is DataResult.Success -> if (result.data.status == IturActivityStatus.ONGOING) return result.data
                is DataResult.Error -> throw BackendInitializationException(result.message)
                is DataResult.NotFound -> Unit
            }
        }

        val organized = when (
            val result = activityRepository.getActivities(
                ActivityFilter.OngoingByOrganizer(user.id),
            )
        ) {
            is DataResult.Success -> result.data.firstOrNull()
            is DataResult.Error -> throw BackendInitializationException(result.message)
            is DataResult.NotFound -> null
        }
        return organized ?: when (
            val result = activityRepository.getActivities(
                ActivityFilter.OngoingByParticipant(user.id),
            )
        ) {
            is DataResult.Success -> result.data.firstOrNull()
            is DataResult.Error -> throw BackendInitializationException(result.message)
            is DataResult.NotFound -> null
        }
    }

    /**
     * Starts lifecycle-aware periodic monitoring. The Compose screen calls this on `ON_START`.
     */
    fun startBackendMonitoring() {
        backendHealthCoordinator.start(viewModelScope)
    }

    /**
     * Stops cadence, retries, and in-flight probes while the screen is inactive or exiting.
     */
    fun stopBackendMonitoring() {
        backendHealthCoordinator.stop()
    }

    /**
     * Refreshes participant markers independently of this device's own GPS callback while the
     * map screen is visible. The optional interval keeps the cadence deterministic in tests.
     */
    fun startParticipantLocationMonitoring(
        refreshIntervalMillis: Long = PARTICIPANT_LOCATION_REFRESH_INTERVAL_MILLIS,
    ) {
        if (participantLocationMonitoringJob?.isActive == true) return
        participantLocationMonitoringJob = viewModelScope.launch {
            while (true) {
                delay(refreshIntervalMillis)
                refreshParticipantLocations()
            }
        }
    }

    /** Stops participant-marker polling while the map screen is not visible. */
    fun stopParticipantLocationMonitoring() {
        participantLocationMonitoringJob?.cancel()
        participantLocationMonitoringJob = null
    }

    /**
     * Cancels the pending countdown and probes every service immediately.
     */
    fun retryNow() {
        backendHealthCoordinator.retryNow(viewModelScope)
    }

    private fun reportBackendFailure(
        cause: Throwable,
        assumeAllServices: Boolean = false,
    ): Boolean = backendHealthCoordinator.reportFailure(
        cause,
        assumeAllServices,
        fallbackScope = viewModelScope,
    )

    /**
     * The current user starts an activity, signing in first if they are anonymous.
     */
    fun startActivity(context: Context) {
        viewModelScope.launch {
            val previousState = _uiState.value
            _uiState.value = MapUiState.Loading
            try {
                // Organisers must be signed in; trigger sign-in automatically if needed.
                if (_currentUser.value !is User.RegisteredUser) {
                    val signedIn = performSignIn(context) { startActivity(context) }
                    if (!signedIn) {
                        _uiState.value = previousState
                        return@launch
                    }
                }
                val organizer = requireNotNull(_currentUser.value)

                // MEMB-4B18/MEMB-7A05: starting a second activity while already an active member
                // of one is rejected server-side; check first so the message is specific rather
                // than a generic write failure.
                if (isAlreadyActiveElsewhere(organizer.id, targetActivityId = null)) return@launch

                val result = activityRepository.createActivity(organizerId = organizer.id)
                when (result) {
                    is DataResult.Success -> triggerOngoingState(result.data, context)
                    is DataResult.Error -> {
                        if (!isAlreadyActiveElsewhere(organizer.id, targetActivityId = null)) {
                            backendHealthCoordinator.recheckNow(viewModelScope)
                            _uiState.value = MapUiState.Error(result.message)
                        }
                    }
                    is DataResult.NotFound ->
                        _uiState.value = MapUiState.Error("Activity ${result.id} not found")
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                val message = e.message ?: "Failed to start an activity"
                Log.e("MapViewModel", message, e)
                val organizerId = (_currentUser.value as? User.RegisteredUser)?.id
                if (organizerId != null && isAlreadyActiveElsewhere(organizerId, targetActivityId = null)) {
                    return@launch
                }
                _uiState.value = if (reportBackendFailure(e)) {
                    previousState
                } else {
                    MapUiState.Error(message)
                }
            }
        }
    }

    /**
     * MEMB-4B18/MEMB-7A05: true (and, as a side effect, shows the "already in an activity"
     * message) if [userId] is an active member of an `ONGOING` activity other than
     * [targetActivityId] (`null` when starting a brand-new one, so any active membership blocks
     * it). Used both pre-emptively, before attempting a write the backend would reject anyway,
     * and after a write failure, to recognise that specific rejection (backend-agnostic --
     * re-checks the same repository call rather than inspecting a Firebase-specific exception
     * type) instead of showing a generic error.
     */
    private suspend fun isAlreadyActiveElsewhere(userId: UserId, targetActivityId: IturActivityId?): Boolean {
        val activeElsewhere = when (val result = activityRepository.getActiveActivityId(userId)) {
            is DataResult.Success -> result.data != null && result.data != targetActivityId
            else -> false
        }
        if (activeElsewhere) {
            triggerIdleState("You're already in an activity -- leave it first")
        }
        return activeElsewhere
    }

    /**
     * Explicitly signs in via Google.
     */
    fun signIn(context: Context) {
        viewModelScope.launch {
            performSignIn(context) { signIn(context) }
        }
    }

    private suspend fun performSignIn(
        context: Context,
        retry: () -> Unit,
    ): Boolean = when (val result = userRepository.signIn(context)) {
        is SignInResult.Success -> {
            _currentUser.value = result.user
            _signInPresentation.value = null
            true
        }

        SignInResult.Cancelled -> {
            _signInPresentation.value = null
            false
        }

        is SignInResult.Failure -> {
            _signInPresentation.value = SignInFailurePresentation(
                reason = result.reason,
                onRetry = {
                    _signInPresentation.value = null
                    retry()
                },
                onDismiss = { _signInPresentation.value = null },
            )
            false
        }
    }

    /**
     * Signs out the current user, returning to an anonymous session.
     */
    fun signOut() {
        viewModelScope.launch {
            try {
                userRepository.signOut()
                _currentUser.value = userRepository.getCurrentUser()
                triggerIdleState()
            } catch (e: Exception) {
                Log.e("MapViewModel", "Sign-out failed", e)
                reportBackendFailure(e)
            }
        }
    }

    /**
     * The current user joins an ongoing activity.
     */
    fun joinActivity(activityId: IturActivityId, context: Context) {
        viewModelScope.launch {
            val previousState = _uiState.value
            _uiState.value = MapUiState.Loading
            try {
                val user = currentUser.value ?: userRepository.getCurrentUser().also {
                    _currentUser.value = it
                }

                // MEMB-4B18/MEMB-7A05: joining a second, different ONGOING activity is rejected
                // server-side; check first for a specific message. Already being a member of
                // *this* activity is not a conflict.
                if (isAlreadyActiveElsewhere(user.id, targetActivityId = activityId)) return@launch

                // Join the activity.
                val result = activityRepository.addParticipant(activityId, user.id)
                // Change the UI state.
                when (result) {
                    is DataResult.Success -> triggerOngoingState(result.data, context)
                    is DataResult.Error -> {
                        if (!isAlreadyActiveElsewhere(user.id, targetActivityId = activityId)) {
                            backendHealthCoordinator.recheckNow(viewModelScope)
                            _uiState.value = MapUiState.Error(result.message)
                        }
                    }
                    is DataResult.NotFound ->
                        _uiState.value =
                            MapUiState.Error("Activity ${result.id} not found")
                }
            } catch (e: Exception) {
                val message = "Failed to join activity $activityId"
                Log.e("MapViewModel", message, e)
                val userId = _currentUser.value?.id
                if (userId != null && isAlreadyActiveElsewhere(userId, targetActivityId = activityId)) {
                    return@launch
                }
                _uiState.value = if (reportBackendFailure(e)) {
                    previousState
                } else {
                    MapUiState.Error(message)
                }
            }
        }
    }

    /**
     * Stop participating in the current activity.
     */
    fun leaveActivity() {
        viewModelScope.launch {
            // If there is an ongoing activity...
            _ongoingActivityId.value?.let { activityId ->
                try {
                    currentUser.value?.let {
                        if (_organizerId.value == it.id) {
                            // If it's the organiser, finish the activity for everyone and clean
                            // up every participant's location data.
                            // CAUTION: it needs to happen before removing the participant,
                            // thus revoking write access.
                            locationsRepository.removeForActivity(activityId)
                            requireSuccessfulBackendWrite(
                                activityRepository.updateActivityStatus(
                                    activityId,
                                    IturActivityStatus.FINISHED,
                                ),
                            )
                        } else {
                            // Otherwise, only clean up this participant's own location data.
                            // CAUTION: it needs to happen before removing the participant,
                            // thus revoking write access.
                            locationsRepository.removeForParticipant(it.id, activityId)
                        }
                        // Remove the participants from the activity.
                        requireSuccessfulBackendWrite(
                            activityRepository.removeParticipant(activityId, it.id),
                        )
                    }

                    // Set the next state.
                    triggerIdleState("You are no longer participating in an activity")
                } catch (e: Exception) {
                    val message = "Failed to leave activity $activityId"
                    Log.e("MapViewModel", message, e)
                    if (!reportBackendFailure(e)) {
                        _uiState.value = MapUiState.Error(message)
                    }
                }
            }
        }
    }

    fun triggerIdleState(message: String? = null) {
        _ongoingActivityId.value = null
        _participantLocations.value = emptyList()
        _uiState.value = MapUiState.Idle(message)
        lastBroadcastSeen = null
        _latestBroadcast.value = null
    }

    private fun requireSuccessfulBackendWrite(result: DataResult<*>) {
        when (result) {
            is DataResult.Success -> Unit
            is DataResult.Error -> throw BackendOperationException(result.message)
            is DataResult.NotFound -> throw BackendOperationException(
                "Backend record ${result.id} was not found",
            )
        }
    }

    suspend fun triggerOngoingState(activityId: IturActivityId, context: Context) {
        val result = activityRepository.getActivity(activityId)
        when (result) {
            is DataResult.Success ->
                triggerOngoingState(activity = result.data, context)

            is DataResult.NotFound -> {
                Log.e(
                    "MapViewModel",
                    "Could not trigger the ongoing state, activity ${result.id} not found",
                )
                _uiState.value = MapUiState.RecoverableError(
                    message = "The ongoing activity could not be resumed.",
                    onRetry = { viewModelScope.launch { triggerOngoingState(activityId, context) } },
                    onCancel = { triggerIdleState() },
                )
            }

            is DataResult.Error -> {
                Log.e("MapViewModel", "Could not trigger the ongoing state: ${result.message}")
                backendHealthCoordinator.recheckNow(viewModelScope)
                _uiState.value = MapUiState.RecoverableError(
                    message = "The ongoing activity could not be resumed.",
                    onRetry = { viewModelScope.launch { triggerOngoingState(activityId, context) } },
                    onCancel = { triggerIdleState() },
                )
            }
        }
    }

    private suspend fun triggerOngoingState(activity: IturActivity, context: Context) {
        val locations = locationsRepository.getForActivity(activity.id)
        // Keep a record of activity's organiser ID.
        _organizerId.value = activity.organizerId
        // Select the joined activity as the current one.
        _ongoingActivityId.value = activity.id
        // Show the ongoing activity state.
        _participantLocations.value = locations
        val organizer = try {
            userRepository.getAll(listOf(activity.organizerId))
                .firstOrNull() ?: AnonymousUser(activity.organizerId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            // User profiles are not required to restore the activity. In particular, a
            // participant can read their activity without being allowed to read the organizer's
            // private profile; preserve the backend-derived ongoing state with an ID-only label.
            Log.w("MapViewModel", "Organizer profile unavailable; using its activity ID", failure)
            AnonymousUser(activity.organizerId)
        }
        _uiState.value = Ongoing(
            activity = activity,
            organizer = organizer,
            participantIds = activity.participantIds,
            locations = locations,
        )
        // Start updating the location.
        startLocationUpdates(context)
        // Reset broadcast tracking for the new activity; the UI drives the actual polling
        // (see pollBroadcastsOnce and MapScreen's LaunchedEffect).
        lastBroadcastSeen = null
        _latestBroadcast.value = null

        Log.d(
            "MapViewModel",
            "User ${currentUser.value} joined activity ${activity.id}",
        )
    }

    /**
     * One polling tick for operator broadcasts on the current ongoing activity (UC-ACTIVITY-007):
     * notifies (system notification plus in-app banner via [latestBroadcast]) for each broadcast
     * not yet seen. No-ops if there is no ongoing activity. Call this repeatedly at an interval
     * from the UI while an activity is ongoing -- polling, not a real-time listener, to match this
     * app's existing backend-access pattern (see e.g. [initialize], which also polls rather than
     * subscribing).
     */
    suspend fun pollBroadcastsOnce() {
        val activityId = _ongoingActivityId.value ?: return
        try {
            activityRepository.getBroadcastsSince(activityId, lastBroadcastSeen).forEach { broadcast ->
                broadcastNotifier.notify(broadcast)
                _latestBroadcast.value = broadcast
                lastBroadcastSeen = broadcast.sentOn
            }
        } catch (e: Exception) {
            Log.e("MapViewModel", "Failed to poll broadcasts for $activityId", e)
            reportBackendFailure(e)
        }
    }

    private suspend fun refreshParticipantLocations() {
        val activityId = _ongoingActivityId.value ?: return
        try {
            _participantLocations.value = locationsRepository.getForActivity(activityId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("MapViewModel", "Failed to refresh participant locations for $activityId", e)
            reportBackendFailure(e)
        }
    }

    /**
     * Posts the location of the current user along with the activity.
     */
    private suspend fun updateUserLocation(
        userId: UserId,
        activityId: IturActivityId,
        location: Location,
    ) {
        try {
            locationsRepository.updateForParticipant(
                userId,
                activityId,
                IturLocation(latitude = location.latitude, longitude = location.longitude),
            )
            refreshParticipantLocations()
        } catch (e: Exception) {
            Log.e(
                "MapViewModel",
                "Failed to update the location of user ${userId.value} in activity ${activityId.value}",
                e,
            )
            reportBackendFailure(e)
        }
    }

    /**
     * A participant requests attention from the organiser.
     */
    fun requestAttention() {
        val activityId = _ongoingActivityId.value ?: return
        val userId = currentUser.value?.id ?: return
        viewModelScope.launch {
            try {
                activityRepository.requestAttention(activityId, userId)
            } catch (e: Exception) {
                Log.e("MapViewModel", "Failed to request attention for activity $activityId", e)
                reportBackendFailure(e)
            }
        }
    }

    // The callback to use when the device's location is received.
    private val locationCallback: (Location) -> Unit = { location ->
        _lastLocation.postValue(location)
        // If there's an activity ID and a user,
        // update the user's location for that activity.
        ongoingActivityId.value?.let { activityId ->
            currentUser.value?.let { participant ->
                // Tied to viewModelScope (instead of an unmanaged CoroutineScope) so this
                // work is cancelled along with the rest of the ViewModel's work, rather
                // than continuing to run -- and potentially update now-stale state --
                // after the ViewModel is cleared.
                viewModelScope.launch(Dispatchers.IO) {
                    updateUserLocation(participant.id, activityId, location)
                }
            }
        }
    }

    /**
     * Starts collecting the device's location.
     */
    fun startLocationUpdates(context: Context) {
        if (locationUpdatesActive) return
        Log.d("MapScreen", "Checking location permissions")
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED &&
            !locationUpdatesActive
        ) {
            Log.d("MapScreen", "Requesting location updates")
            locationClient.requestUpdates(
                locationUpdateConfig.updateIntervalMillis,
                locationCallback,
            )
            locationUpdatesActive = true
            Log.d("MapScreen", "Location updates requested successfully")
        } else {
            Log.d("MapScreen", "No location permission...")
        }
    }

    /**
     * Stops collecting the device's location.
     */
    fun stopLocationUpdates() {
        if (!locationUpdatesActive) return
        locationClient.removeUpdates(locationCallback)
        locationUpdatesActive = false
    }

    /** Applies a runtime permission change without recreating the map or activity shell. */
    fun onLocationPermissionChanged(granted: Boolean, context: Context) {
        if (granted && _ongoingActivityId.value != null) {
            startLocationUpdates(context)
        } else if (!granted) {
            stopLocationUpdates()
        }
    }
}

sealed interface MapUiState {
    data object Loading : MapUiState

    data class Idle(
        val message: String? = null,
    ) : MapUiState

    data class Ongoing(
        val activity: IturActivity,
        val organizer: User,
        val participantIds: List<UserId>,
        val locations: List<ParticipantLocation>,
    ) : MapUiState

    data class Error(
        val message: String,
    ) : MapUiState

    class RecoverableError(
        val message: String,
        val onRetry: () -> Unit,
        val onCancel: () -> Unit,
    ) : MapUiState
}

class SignInFailurePresentation(
    val reason: SignInFailureReason,
    val onRetry: () -> Unit,
    val onDismiss: () -> Unit,
) {
    val message: String
        get() = when (reason) {
            SignInFailureReason.NO_ACCOUNT ->
                "No Google account is available. Add an account and try again."
            SignInFailureReason.NOT_CONFIGURED ->
                "Sign-in isn't configured for this app."
            SignInFailureReason.SERVICE_UNAVAILABLE ->
                "Sign-in is temporarily unavailable. Check your connection and try again."
            SignInFailureReason.UNEXPECTED ->
                "Sign-in couldn't be completed. Try again."
        }

    val retryable: Boolean
        get() = reason != SignInFailureReason.NOT_CONFIGURED
}

data class BackendAvailabilityUiState(
    val failingServices: List<BackendService> = emptyList(),
    val retryCountdown: Int? = null,
)

private class BackendInitializationException(message: String) : Exception(message)
private class BackendOperationException(message: String) : Exception(message)

private const val PARTICIPANT_LOCATION_REFRESH_INTERVAL_MILLIS = 15_000L
