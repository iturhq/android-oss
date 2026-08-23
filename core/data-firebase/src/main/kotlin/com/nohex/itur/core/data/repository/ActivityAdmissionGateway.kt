/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.data.repository

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.nohex.itur.core.domain.id.IturActivityId
import com.nohex.itur.core.model.ParticipantSignal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

internal interface ActivityAdmissionGateway {
    suspend fun start(activityId: IturActivityId? = null): DataResult<IturActivityId>

    suspend fun join(activityId: IturActivityId): DataResult<IturActivityId>

    suspend fun leave(activityId: IturActivityId): DataResult<IturActivityId>

    suspend fun setParticipantSignal(
        activityId: IturActivityId,
        signal: ParticipantSignal?,
    ): DataResult<IturActivityId>
}

internal class FirebaseFunctionsActivityAdmissionGateway(
    private val functions: FirebaseFunctions,
) : ActivityAdmissionGateway {
    @Suppress("MaxLineLength")
    override suspend fun start(activityId: IturActivityId?): DataResult<IturActivityId> = call("startActivity", activityId)

    @Suppress("MaxLineLength")
    override suspend fun join(activityId: IturActivityId): DataResult<IturActivityId> = call("joinActivity", activityId)

    @Suppress("MaxLineLength")
    override suspend fun leave(activityId: IturActivityId): DataResult<IturActivityId> = call("leaveActivity", activityId)

    override suspend fun setParticipantSignal(
        activityId: IturActivityId,
        signal: ParticipantSignal?,
    ): DataResult<IturActivityId> = call(
        "setParticipantSignal",
        mapOf("activityId" to activityId.value, "signal" to signal?.name),
    )

    private suspend fun call(
        functionName: String,
        activityId: IturActivityId?,
    ): DataResult<IturActivityId> = call(
        functionName,
        activityId?.let { mapOf("activityId" to it.value) } ?: emptyMap<String, String>(),
    )

    @Suppress("TooGenericExceptionCaught")
    private suspend fun call(
        functionName: String,
        request: Map<String, Any?>,
    ): DataResult<IturActivityId> = try {
        val response = functions.getHttpsCallable(functionName).call(request).await()
        val responseActivityId = (response.data as? Map<*, *>)?.get("activityId") as? String
        if (responseActivityId.isNullOrBlank()) {
            DataResult.Error("The activity service returned an invalid response")
        } else {
            DataResult.Success(IturActivityId(responseActivityId))
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        val reason = ((failure as? FirebaseFunctionsException)?.details as? Map<*, *>)
            ?.get("reason") as? String
        when (reason) {
            "activity-full" -> DataResult.Error(
                message = "This activity is full.",
                code = DataErrorCode.ACTIVITY_FULL,
            )
            "activity-start-limit-reached" -> DataResult.Error(
                message = "Activity start limit reached.",
                code = DataErrorCode.ACTIVITY_START_LIMIT_REACHED,
            )
            else -> DataResult.Error(failure.message ?: "Activity admission failed")
        }
    }
}
