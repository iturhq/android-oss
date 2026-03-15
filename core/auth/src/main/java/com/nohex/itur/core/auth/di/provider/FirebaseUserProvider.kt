/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.auth.di.provider

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseUserProvider @Inject constructor() : UserProvider {
    override fun getUserId(context: Context): String? = FirebaseAuth.getInstance().currentUser?.uid
}
