/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.data.repository

import com.google.android.gms.tasks.Tasks
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.HttpsCallableReference
import com.nohex.itur.core.domain.id.IturActivityId
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ActivityAdmissionGatewayTest {
    private val functions = mockk<FirebaseFunctions>()
    private val callable = mockk<HttpsCallableReference>()
    private val gateway = FirebaseFunctionsActivityAdmissionGateway(functions)

    @Test
    fun `GIVEN backend rejects capacity WHEN joining THEN neutral structured error is mapped`() = runBlocking {
        every { functions.getHttpsCallable("joinActivity") } returns callable
        val failure = mockk<FirebaseFunctionsException> {
            every { message } returns "This activity is full."
            every { details } returns mapOf("reason" to "activity-full")
        }
        every { callable.call(mapOf("activityId" to "Activity000000000001")) } returns
            Tasks.forException(failure)

        val result = gateway.join(IturActivityId("Activity000000000001"))

        val error = assertIs<DataResult.Error>(result)
        assertEquals(DataErrorCode.ACTIVITY_FULL, error.code)
        assertEquals("This activity is full.", error.message)
    }

    @Test
    fun `GIVEN backend rejects monthly start WHEN starting THEN neutral structured error is mapped`() = runBlocking {
        every { functions.getHttpsCallable("startActivity") } returns callable
        val failure = mockk<FirebaseFunctionsException> {
            every { message } returns "The activity start limit for this month has been reached."
            every { details } returns mapOf("reason" to "activity-start-limit-reached")
        }
        every { callable.call(emptyMap<String, String>()) } returns Tasks.forException(failure)

        val result = gateway.start()

        val error = assertIs<DataResult.Error>(result)
        assertEquals(DataErrorCode.ACTIVITY_START_LIMIT_REACHED, error.code)
        assertEquals("Activity start limit reached.", error.message)
    }

    @Test
    fun `WHEN leaving THEN trusted leave callable receives the activity id`() = runBlocking {
        every { functions.getHttpsCallable("leaveActivity") } returns callable
        every { callable.call(mapOf("activityId" to "Activity000000000001")) } returns
            Tasks.forResult(mockk {
                every { data } returns mapOf("activityId" to "Activity000000000001")
            })

        val result = gateway.leave(IturActivityId("Activity000000000001"))

        assertEquals(IturActivityId("Activity000000000001"), assertIs<DataResult.Success<IturActivityId>>(result).data)
    }
}
