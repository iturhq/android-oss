/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.auth.di.provider

import android.content.Context

interface UserProvider {
    fun getUserId(context: Context): String?
}
