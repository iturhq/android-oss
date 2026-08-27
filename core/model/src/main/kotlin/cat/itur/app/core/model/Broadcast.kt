/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.core.model

import java.util.Date

/**
 * An operator-sent alert to everyone in an activity (see UC-ACTIVITY-007). Sent only from
 * `itur-admin`; Android is read-only for these.
 */
data class Broadcast(
    val id: String,
    val message: String,
    val sentOn: Date,
)
