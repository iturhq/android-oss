/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.feature.map.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY
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
import com.nohex.itur.core.model.IturActivity
import com.nohex.itur.core.model.IturActivityStatus
import com.nohex.itur.core.model.ParticipantLocation
import com.nohex.itur.feature.map.ui.MapUiState.Ongoing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.nohex.itur.core.model.Location as IturLocation

@HiltViewModel
class MapViewModel @Inject
constructor(
    private val activityRepository: ActivityRepository,
    private val userRepository: UserRepository,
    private val locationsRepository: LocationRepository,
    private val locationClient: LocationClient,
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

    init {
        viewModelScope.launch {
            _currentUser.value = userRepository.getCurrentUser()

            // If there is an ongoing activity organised by the current user, join it.
            _currentUser.value?.let {
                val result =
                    activityRepository.getActivities(ActivityFilter.OngoingByOrganizer(it.id))
                if (result is DataResult.Success) {
                    result.data.firstOrNull()?.let { ongoingActivity ->
                        _ongoingActivityId.value = ongoingActivity.id
                    }
                }
            }
        }
    }

    /**
     * The current user starts an activity, signing in first if they are anonymous.
     */
    fun startActivity(context: Context) {
        viewModelScope.launch {
            _uiState.value = MapUiState.Loading
            try {
                // Organisers must be signed in; trigger sign-in automatically if needed.
                if (_currentUser.value !is User.RegisteredUser) {
                    _currentUser.value = userRepository.signIn(context)
                }
                val organizer = requireNotNull(_currentUser.value)
                val result = activityRepository.createActivity(organizerId = organizer.id)
                when (result) {
                    is DataResult.Success -> triggerOngoingState(result.data, context)
                    is DataResult.Error -> _uiState.value = MapUiState.Error(result.message)
                    is DataResult.NotFound ->
                        _uiState.value = MapUiState.Error("Activity ${result.id} not found")
                }
            } catch (e: Exception) {
                val message = e.message ?: "Failed to start an activity"
                Log.e("MapViewModel", message, e)
                _uiState.value = MapUiState.Error(message)
            }
        }
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
                _uiState.value = MapUiState.Error(message)
            }
        }
    }

    /**
     * Signs out the current user, returning to an anonymous session.
     */
    fun signOut() {
        viewModelScope.launch {
            userRepository.signOut()
            _currentUser.value = userRepository.getCurrentUser()
            triggerIdleState()
        }
    }

    /**
     * The current user joins an ongoing activity.
     */
    fun joinActivity(activityId: IturActivityId, context: Context) {
        viewModelScope.launch {
            _uiState.value = MapUiState.Loading
            try {
                currentUser.value?.let {
                    // Join the activity.
                    val result = activityRepository.addParticipant(activityId, it.id)
                    // Change the UI state.
                    when (result) {
                        is DataResult.Success -> triggerOngoingState(result.data, context)
                        is DataResult.Error -> _uiState.value = MapUiState.Error(result.message)
                        is DataResult.NotFound ->
                            _uiState.value =
                                MapUiState.Error("Activity ${result.id} not found")
                    }
                }
            } catch (e: Exception) {
                val message = "Failed to join activity $activityId"
                Log.e("MapViewModel", message, e)
                _uiState.value = MapUiState.Error(message)
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
                    // Stop requesting the location.
                    stopLocationUpdates()

                    // Clean up location data for the activity.
                    // CAUTION: it needs to happen before removing the participant,
                    // thus revoking write access.
                    locationsRepository.removeForActivity(activityId)

                    currentUser.value?.let {
                        if (_organizerId.value == it.id) {
                            // If it's the organiser, finish the activity for everyone.
                            activityRepository.updateActivityStatus(
                                activityId,
                                IturActivityStatus.FINISHED,
                            )
                        }
                        // Remove the participants from the activity.
                        activityRepository.removeParticipant(activityId, it.id)
                    }

                    // Set the next state.
                    triggerIdleState("You are no longer participating in an activity")
                } catch (e: Exception) {
                    Log.e("MapViewModel", "Failed to leave activity $activityId", e)
                    _uiState.value = MapUiState.Error("Activity $activityId not found")
                }
            }
        }
    }

    fun triggerIdleState(message: String? = null) {
        _ongoingActivityId.value = null
        _uiState.value = MapUiState.Idle(message)
        stopLocationUpdates()
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
        _uiState.value = Ongoing(
            activity = activity,
            organizer = userRepository.getAll(listOf(activity.organizerId))
                .firstOrNull() ?: AnonymousUser(activity.organizerId),
            participantIds = activity.participantIds,
            locations = locationsRepository.getForActivity(activity.id),
        )

        // Start updating the location.
        startLocationUpdates(context)

        Log.d(
            "MapViewModel",
            "User ${currentUser.value} joined activity ${activity.id}",
        )
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
            // Update the participants location list.
            _participantLocations.value = locationsRepository.getForActivity(activityId)
        } catch (e: Exception) {
            Log.e(
                "MapViewModel",
                "Failed to update the location of user ${userId.value} in activity ${activityId.value}",
                e,
            )
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
            }
        }
    }

    // The callback to use when the device's location is received.
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.locations.lastOrNull()?.let { location ->
                _lastLocation.postValue(location)
                // If there's an activity ID and a user,
                // update the user's location for that activity.
                ongoingActivityId.value?.let { activityId ->
                    currentUser.value?.let { participant ->
                        CoroutineScope(Dispatchers.IO).launch {
                            updateUserLocation(participant.id, activityId, location)
                        }
                    }
                }
            }
        }
    }

    /**
     * Starts collecting the device's location.
     */
    private fun startLocationUpdates(context: Context) {
        Log.d("MapScreen", "Checking location permissions")
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            Log.d("MapScreen", "Requesting location updates")
            locationClient.requestUpdates(
                LocationRequest.Builder(PRIORITY_HIGH_ACCURACY, 2000).build(),
                locationCallback,
                Looper.getMainLooper(),
            )
            Log.d("MapScreen", "Location updates requested successfully")
        } else {
            Log.d("MapScreen", "No location permission...")
        }
    }

    /**
     * Stops collecting the device's location.
     */
    private fun stopLocationUpdates() {
        locationClient.removeUpdates(locationCallback)
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
