/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
plugins {
    alias(libs.plugins.android.library)
    // Hilt
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.nohex.itur.core.location"
    compileSdk = 36
}

dependencies {

    // Hilt
    implementation(libs.hilt.android)
    implementation(libs.play.services.location)
    implementation(libs.androidx.core.ktx)
    ksp(libs.hilt.compiler)
    // Runtime
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.runtime)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.mockk)
}