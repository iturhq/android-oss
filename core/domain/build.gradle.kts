/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.nohex.itur.core.domain"
    compileSdk = 36
}

dependencies {
    implementation(libs.androidx.core.ktx)
}
