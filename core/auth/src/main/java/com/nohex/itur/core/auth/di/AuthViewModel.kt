/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.auth.di

import androidx.lifecycle.ViewModel
import com.nohex.itur.core.data.repository.UserRepository
import com.nohex.itur.core.domain.id.UserId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _userId = MutableStateFlow<UserId?>(null)
    val userId: StateFlow<UserId?> = _userId.asStateFlow()

    suspend fun refresh() {
        _userId.value = userRepository.getCurrentUser().id
    }
}
