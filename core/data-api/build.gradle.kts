/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.nohex.itur.core.data.api"
    compileSdk = 36
}

dependencies {
    // These types appear directly in this module's own interfaces' public signatures.
    api(projects.core.domain)
    api(projects.core.model)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit.junit)
}
