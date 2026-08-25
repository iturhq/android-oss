/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "cat.itur.app.core.model"
    compileSdk = 36
}

dependencies {
    implementation(projects.core.domain)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit.junit)
}