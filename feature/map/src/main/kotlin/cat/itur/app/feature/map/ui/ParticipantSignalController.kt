/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map.ui

import cat.itur.app.core.data.repository.DataResult
import cat.itur.app.core.data.repository.ParticipantSignalRepository
import cat.itur.app.core.domain.id.IturActivityId
import cat.itur.app.core.domain.id.UserId
import cat.itur.app.core.model.IturActivity
import cat.itur.app.core.model.ParticipantSignal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class ParticipantSignalController(
    private val repository: ParticipantSignalRepository,
    private val scope: CoroutineScope,
    private val callbacks: ParticipantSignalCallbacks,
) {
    private val mutableState = MutableStateFlow(ParticipantSignalUiState())
    val state = mutableState.asStateFlow()
    private val mutableMessage = MutableStateFlow<String?>(null)
    val message = mutableMessage.asStateFlow()
    private val mutableParticipantMap = MutableStateFlow<Map<UserId, ParticipantSignal>>(emptyMap())
    val participantMap = mutableParticipantMap.asStateFlow()
    var mutationVersion = 0L
        private set

    fun restore(activity: IturActivity, userId: UserId?) {
        mutationVersion++
        mutableParticipantMap.value = activity.currentParticipantSignals()
        mutableState.value = ParticipantSignalUiState(
            confirmed = userId?.let(activity.participantSignals::get),
        )
        mutableMessage.value = null
    }

    fun refresh(
        activity: IturActivity,
        userId: UserId?,
        expectedMutationVersion: Long,
    ): IturActivity {
        val currentState = mutableState.value
        val preserveLocal = currentState.inFlight != null || mutationVersion != expectedMutationVersion
        val refreshed = activity.currentParticipantSignals()
        val effective = if (preserveLocal && userId != null) {
            val localSignal = mutableParticipantMap.value[userId]
            if (localSignal == null) refreshed - userId else refreshed + (userId to localSignal)
        } else {
            refreshed
        }
        mutableParticipantMap.value = effective
        if (!preserveLocal) {
            mutableState.value = currentState.copy(
                confirmed = userId?.let(activity.participantSignals::get),
            )
        }
        return activity.copy(participantSignals = effective)
    }

    fun clear() {
        mutationVersion++
        mutableParticipantMap.value = emptyMap()
        mutableState.value = ParticipantSignalUiState()
        mutableMessage.value = null
    }

    fun acknowledgeMessage() {
        mutableMessage.value = null
    }

    fun update(signal: ParticipantSignal?) {
        val request = request(signal) ?: return
        mutationVersion++
        mutableState.value = request.current.copy(inFlight = signal.asAction())
        scope.launch {
            runUpdate(request, signal)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun runUpdate(
        request: ParticipantSignalRequest,
        signal: ParticipantSignal?,
    ) {
        try {
            val result = if (signal == null) {
                repository.clearParticipantSignal(request.context.activityId, request.context.userId)
            } else {
                repository.setParticipantSignal(
                    request.context.activityId,
                    request.context.userId,
                    signal,
                )
            }
            if (callbacks.currentContext() != request.context) return
            when (result) {
                is DataResult.Success -> signalUpdated(result.data, request.context.userId)
                is DataResult.Error -> signalFailed(result.message)
                is DataResult.NotFound -> signalFailed("Activity ${result.id} was not found")
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            if (callbacks.currentContext() == request.context) {
                signalFailed(failure.message)
                callbacks.onFailure(request.context.activityId, failure)
            }
        }
    }

    private fun request(signal: ParticipantSignal?): ParticipantSignalRequest? {
        val context = callbacks.currentContext()
        val current = mutableState.value
        val allowed = context != null &&
            !context.isOrganizer &&
            current.inFlight == null &&
            signal != current.confirmed &&
            (current.confirmed != ParticipantSignal.NEEDS_HELP || signal == null) &&
            (signal != null || current.confirmed != null)
        return if (allowed) {
            ParticipantSignalRequest(requireNotNull(context), current)
        } else {
            null
        }
    }

    private fun signalUpdated(activity: IturActivity, userId: UserId) {
        mutationVersion++
        mutableParticipantMap.value = activity.currentParticipantSignals()
        callbacks.onActivityUpdated(activity)
        mutableState.value = ParticipantSignalUiState(
            confirmed = activity.participantSignals[userId],
        )
    }

    private fun signalFailed(detail: String?) {
        mutationVersion++
        mutableState.value = mutableState.value.copy(inFlight = null)
        mutableMessage.value = buildString {
            append("Your safety status could not be updated. Please try again.")
            if (!detail.isNullOrBlank()) append(" ").append(detail)
        }
    }

    private fun IturActivity.currentParticipantSignals(): Map<UserId, ParticipantSignal> {
        val participants = participantIds.toSet()
        return participantSignals.filterKeys { it in participants }
    }
}

internal data class ParticipantSignalCallbacks(
    val currentContext: () -> ParticipantSignalContext?,
    val onActivityUpdated: (IturActivity) -> Unit,
    val onFailure: (IturActivityId, Exception) -> Unit,
)

internal data class ParticipantSignalContext(
    val activityId: IturActivityId,
    val userId: UserId,
    val isOrganizer: Boolean,
)

private data class ParticipantSignalRequest(
    val context: ParticipantSignalContext,
    val current: ParticipantSignalUiState,
)
