/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.core.model

import cat.itur.app.core.domain.id.UserId

fun IturActivity.isOrganizer(userId: UserId): Boolean = organizerId == userId
