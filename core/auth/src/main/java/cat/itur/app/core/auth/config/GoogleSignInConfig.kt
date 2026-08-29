/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.core.auth.config

/**
 * Google Sign-In configuration owned by the consuming application, not this module. Supplied via
 * Hilt by whichever application assembles the dependency graph (see the `prod`/`local` flavors'
 * [cat.itur.app.core.auth.repository.FirebaseUserRepository]).
 */
data class GoogleSignInConfig(
    val webClientId: String,
)
