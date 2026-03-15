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
    namespace = "com.nohex.itur.core.datastore"
    compileSdk = 36
}

dependencies {
    api(libs.androidx.datastore)
    api(project(":core:datastore-proto-jvm"))

    implementation(projects.core.model)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.core.ktx)
    // Hilt
    ksp (libs.hilt.compiler)
    implementation (libs.hilt.android)
}
