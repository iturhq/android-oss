/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.model

import com.nohex.itur.core.domain.id.UserId

fun IturActivity.isOrganizer(userId: UserId): Boolean = organizerId == userId
