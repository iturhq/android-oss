/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.core.data.repository

import cat.itur.app.core.domain.id.IturActivityId
import cat.itur.app.core.model.ParticipantSignal
import com.google.android.gms.tasks.Tasks
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.HttpsCallableReference
import com.google.firebase.functions.HttpsCallableResult
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
            Tasks.forResult(
                HttpsCallableResult::class.java
                    .getConstructor(Any::class.java)
                    .newInstance(mapOf("activityId" to "Activity000000000001")),
            )

        val result = gateway.leave(IturActivityId("Activity000000000001"))

        assertEquals(IturActivityId("Activity000000000001"), assertIs<DataResult.Success<IturActivityId>>(result).data)
    }

    @Test
    fun `WHEN setting a signal THEN the trusted callable receives no participant id`() = runBlocking {
        every { functions.getHttpsCallable("setParticipantSignal") } returns callable
        every {
            callable.call(
                mapOf(
                    "activityId" to "Activity000000000001",
                    "signal" to ParticipantSignal.DELAYED.name,
                ),
            )
        } returns Tasks.forResult(
            HttpsCallableResult::class.java
                .getConstructor(Any::class.java)
                .newInstance(mapOf("activityId" to "Activity000000000001")),
        )

        val result = gateway.setParticipantSignal(
            IturActivityId("Activity000000000001"),
            ParticipantSignal.DELAYED,
        )

        assertEquals(IturActivityId("Activity000000000001"), assertIs<DataResult.Success<IturActivityId>>(result).data)
    }

    @Test
    fun `WHEN clearing a signal THEN the trusted callable sends an explicit null tombstone`() = runBlocking {
        every { functions.getHttpsCallable("setParticipantSignal") } returns callable
        every {
            callable.call(
                mapOf<String, Any?>(
                    "activityId" to "Activity000000000001",
                    "signal" to null,
                ),
            )
        } returns Tasks.forResult(
            HttpsCallableResult::class.java
                .getConstructor(Any::class.java)
                .newInstance(mapOf("activityId" to "Activity000000000001")),
        )

        val result = gateway.setParticipantSignal(IturActivityId("Activity000000000001"), null)

        assertEquals(IturActivityId("Activity000000000001"), assertIs<DataResult.Success<IturActivityId>>(result).data)
    }

    @Test
    fun `GIVEN a callable response without an activity id WHEN joining THEN the invalid response is rejected`() = runBlocking {
        every { functions.getHttpsCallable("joinActivity") } returns callable
        every { callable.call(mapOf("activityId" to "Activity000000000001")) } returns
            Tasks.forResult(
                HttpsCallableResult::class.java
                    .getConstructor(Any::class.java)
                    .newInstance(mapOf("unexpected" to "response")),
            )

        val result = gateway.join(IturActivityId("Activity000000000001"))

        assertEquals(
            "The activity service returned an invalid response",
            assertIs<DataResult.Error>(result).message,
        )
    }

    @Test
    fun `GIVEN an unclassified callable failure without a message WHEN leaving THEN a stable fallback is returned`() = runBlocking {
        every { functions.getHttpsCallable("leaveActivity") } returns callable
        every { callable.call(mapOf("activityId" to "Activity000000000001")) } returns
            Tasks.forException(IllegalStateException())

        val result = gateway.leave(IturActivityId("Activity000000000001"))

        assertEquals("Activity admission failed", assertIs<DataResult.Error>(result).message)
    }

    @Test
    fun `GIVEN callable cancellation WHEN starting THEN cancellation is propagated`() = runBlocking {
        every { functions.getHttpsCallable("startActivity") } returns callable
        every { callable.call(emptyMap<String, String>()) } returns
            Tasks.forException(CancellationException("caller cancelled"))

        val failure = assertFailsWith<CancellationException> { gateway.start() }

        assertEquals("caller cancelled", failure.message)
    }
}
