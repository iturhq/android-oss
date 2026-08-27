/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.core.data.repository

import com.google.android.gms.tasks.Task
import io.mockk.every
import io.mockk.mockk

/**
 * `Task.await()` (kotlinx-coroutines-play-services) takes a fast path when [Task.isComplete] is
 * already true, reading [Task.exception]/[Task.isCanceled]/[Task.result] synchronously without
 * ever registering a completion listener. Stubbing just those four members is therefore enough
 * to drive `await()` in a plain JVM unit test, with no Robolectric or real Play Services runtime.
 */
internal fun <T> successfulTask(result: T): Task<T> {
    val task = mockk<Task<T>>()
    every { task.isComplete } returns true
    every { task.isCanceled } returns false
    every { task.exception } returns null
    every { task.result } returns result
    return task
}

internal fun <T> failedTask(taskException: Exception): Task<T> {
    val task = mockk<Task<T>>()
    every { task.isComplete } returns true
    every { task.isCanceled } returns false
    every { task.exception } returns taskException
    return task
}
