/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.feature.map.ui

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
import com.nohex.itur.core.data.health.BackendHealthCheck
import com.nohex.itur.core.data.health.BackendService
import com.nohex.itur.core.data.repository.ActivityFilter
import com.nohex.itur.core.data.repository.ActivityRepository
import com.nohex.itur.core.data.repository.DataResult
import com.nohex.itur.core.data.repository.LocationRepository
import com.nohex.itur.core.data.repository.UserRepository
import com.nohex.itur.core.domain.id.IturActivityId
import com.nohex.itur.core.domain.id.UserId
import com.nohex.itur.core.domain.model.User
import com.nohex.itur.core.domain.model.User.AnonymousUser
import com.nohex.itur.core.location.LocationClient
import com.nohex.itur.core.model.Broadcast
import com.nohex.itur.core.model.IturActivity
import com.nohex.itur.core.model.IturActivityStatus
import com.nohex.itur.core.model.ParticipantLocation
import com.nohex.itur.feature.map.config.LocationUpdateConfig
import com.nohex.itur.feature.map.config.MapStyleConfig
import com.nohex.itur.feature.map.notifications.BroadcastNotifier
import com.nohex.itur.feature.map.ui.MapUiState.Ongoing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.Date
import javax.inject.Inject
import com.nohex.itur.core.model.Location as IturLocation

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
    private val backendHealthChecks: Set<@JvmSuppressWildcards BackendHealthCheck>,
) : ViewModel() {
    private val _uiState = MutableStateFlow<MapUiState>(MapUiState.Idle())
    val uiState = _uiState.asStateFlow()

    // The current user.
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser = _currentUser.asStateFlow()

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
    private var backendRetryAttempt = 0
    private var backendRetryJob: Job? = null
    private var backendCheckJob: Job? = null
    private var backendCadenceJob: Job? = null
    private var backendMonitoringActive = false
    private var participantLocationMonitoringJob: Job? = null
    private var initialRestorePending = true
    private var locationUpdatesActive = false

    // The most recent operator broadcast (UC-ACTIVITY-007), for an in-app banner alongside
    // the system notification posted by [broadcastNotifier]. Polling itself is driven by the UI
    // (a Compose LaunchedEffect tied to the ongoing activity, see MapScreen), not by the
    // ViewModel, so it naturally stops when the screen isn't showing an ongoing activity and
    // doesn't require an unbounded viewModelScope loop that's awkward to unit test.
    private val _latestBroadcast = MutableStateFlow<Broadcast?>(null)
    val latestBroadcast: StateFlow<Broadcast?> = _latestBroadcast.asStateFlow()
    private var lastBroadcastSeen: Date? = null

    init {
        requestHealthCheck()
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
        backendMonitoringActive = true
        if (initialRestorePending) {
            requestHealthCheck()
        } else if (_backendAvailability.value.failingServices.isEmpty()) {
            scheduleNextCadenceCheck()
        } else {
            scheduleBackendRetry()
        }
    }

    /**
     * Stops cadence, retries, and in-flight probes while the screen is inactive or exiting.
     */
    fun stopBackendMonitoring() {
        backendMonitoringActive = false
        backendCadenceJob?.cancel()
        backendRetryJob?.cancel()
        backendCheckJob?.cancel()
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

    private fun scheduleNextCadenceCheck() {
        backendCadenceJob?.cancel()
        if (!backendMonitoringActive) return
        backendCadenceJob = viewModelScope.launch {
            delay(BACKEND_MONITOR_INTERVAL_MILLIS)
            performHealthCheck()
        }
    }

    private fun requestHealthCheck(resetBackoff: Boolean = false) {
        if (resetBackoff) backendRetryAttempt = 0
        backendRetryJob?.cancel()
        backendCadenceJob?.cancel()
        backendCheckJob?.cancel()
        backendCheckJob = viewModelScope.launch {
            performHealthCheck()
        }
    }

    private suspend fun performHealthCheck() {
        val failingServices = coroutineScope {
            backendHealthChecks.map { check ->
                async {
                    try {
                        withTimeout(BACKEND_PROBE_TIMEOUT_MILLIS) { check.probe() }
                        null
                    } catch (e: TimeoutCancellationException) {
                        Log.w(
                            "MapViewModel",
                            "Backend probe timed out for ${check.service.id}",
                            e,
                        )
                        check.service
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(
                            "MapViewModel",
                            "Backend probe failed for ${check.service.id}: ${e.message}",
                            e,
                        )
                        check.service
                    }
                }
            }.awaitAll().filterNotNull().sortedBy { it.id }
        }

        if (failingServices.isEmpty()) {
            backendRetryJob?.cancel()
            backendRetryAttempt = 0
            _backendAvailability.value = BackendAvailabilityUiState()
            if (initialRestorePending) restoreInitialState()
            if (_backendAvailability.value.failingServices.isEmpty()) {
                scheduleNextCadenceCheck()
            }
        } else {
            _backendAvailability.value = BackendAvailabilityUiState(failingServices)
            scheduleBackendRetry()
        }
    }

    /**
     * Starts a countdown and then probes every service, preserving bounded exponential backoff.
     */
    private fun scheduleBackendRetry() {
        backendRetryJob?.cancel()
        val failingServices = _backendAvailability.value.failingServices
        if (failingServices.isEmpty() || !backendMonitoringActive) return
        val delaySeconds =
            RETRY_DELAYS_SECONDS.getOrElse(backendRetryAttempt) { RETRY_DELAYS_SECONDS.last() }
        backendRetryJob = viewModelScope.launch {
            for (remaining in delaySeconds downTo 1) {
                _backendAvailability.value = BackendAvailabilityUiState(
                    failingServices = _backendAvailability.value.failingServices,
                    retryCountdown = remaining,
                )
                delay(1_000L)
            }
            backendRetryAttempt++
            // The retry job is now the caller of performHealthCheck. Clear the field so a
            // success or partial-failure transition does not cancel its own coroutine before
            // restoration/probe work finishes.
            backendRetryJob = null
            performHealthCheck()
        }
    }

    /**
     * Cancels the pending countdown and probes every service immediately.
     */
    fun retryNow() {
        requestHealthCheck(resetBackoff = true)
    }

    private fun reportBackendFailure(
        cause: Throwable,
        assumeAllServices: Boolean = false,
    ): Boolean {
        val recognized = backendHealthChecks.filter { it.recognizes(cause) }
        val failedChecks = when {
            recognized.isNotEmpty() -> recognized
            assumeAllServices -> backendHealthChecks.toList()
            else -> {
                requestHealthCheck()
                return false
            }
        }
        val failingById = _backendAvailability.value.failingServices
            .associateBy { it.id }
            .toMutableMap()
        failedChecks.forEach { failingById[it.service.id] = it.service }
        _backendAvailability.value = BackendAvailabilityUiState(
            failingServices = failingById.values.sortedBy { it.id },
        )
        scheduleBackendRetry()
        return failedChecks.isNotEmpty()
    }

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
                    _currentUser.value = userRepository.signIn(context)
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
                            requestHealthCheck()
                            _uiState.value = MapUiState.Error(result.message)
                        }
                    }
                    is DataResult.NotFound ->
                        _uiState.value = MapUiState.Error("Activity ${result.id} not found")
                }
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
            try {
                _currentUser.value = userRepository.signIn(context)
            } catch (e: Exception) {
                val message = e.message ?: "Sign-in failed"
                Log.e("MapViewModel", message, e)
                if (!reportBackendFailure(e)) _uiState.value = MapUiState.Error(message)
            }
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
                            requestHealthCheck()
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
                requestHealthCheck()
                _uiState.value = MapUiState.RecoverableError(
                    message = "The ongoing activity could not be resumed.",
                    onRetry = { viewModelScope.launch { triggerOngoingState(activityId, context) } },
                    onCancel = { triggerIdleState() },
                )
            }
        }
    }

    private suspend fun triggerOngoingState(activity: IturActivity, context: Context) {
        // Keep a record of activity's organiser ID.
        _organizerId.value = activity.organizerId
        // Select the joined activity as the current one.
        _ongoingActivityId.value = activity.id
        // Show the ongoing activity state.
        val locations = locationsRepository.getForActivity(activity.id)
        _participantLocations.value = locations
        _uiState.value = Ongoing(
            activity = activity,
            organizer = userRepository.getAll(listOf(activity.organizerId))
                .firstOrNull() ?: AnonymousUser(activity.organizerId),
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
            ) == PackageManager.PERMISSION_GRANTED
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

data class BackendAvailabilityUiState(
    val failingServices: List<BackendService> = emptyList(),
    val retryCountdown: Int? = null,
)

private class BackendInitializationException(message: String) : Exception(message)
private class BackendOperationException(message: String) : Exception(message)

private val RETRY_DELAYS_SECONDS = listOf(5, 10, 20, 40, 60)
private const val BACKEND_PROBE_TIMEOUT_MILLIS = 5_000L
private const val BACKEND_MONITOR_INTERVAL_MILLIS = 30_000L
private const val PARTICIPANT_LOCATION_REFRESH_INTERVAL_MILLIS = 15_000L
