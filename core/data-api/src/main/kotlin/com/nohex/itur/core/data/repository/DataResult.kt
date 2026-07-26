/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.data.repository

/**
 * The result of a data-related operation.
 */
sealed class DataResult<out T> {
    // The operation succeeded, the resulting object is in [data].
    data class Success<T>(val data: T) : DataResult<T>()

    // The operation could not be completed because the subject was not found.
    data class NotFound(val id: String) : DataResult<Nothing>()

    // The operation could be be completed, details in the message.
    data class Error(val message: String) : DataResult<Nothing>()
}
