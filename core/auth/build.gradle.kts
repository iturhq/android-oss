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
    namespace = "com.nohex.itur.core.auth"
    compileSdk = 36

    buildTypes {
        debug {
            enableUnitTestCoverage = true
        }
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }

    flavorDimensions += "environment"
    productFlavors {
        create("prod") {
            dimension = "environment"
        }
        create("local") {
            dimension = "environment"
        }
        create("demo") {
            dimension = "environment"
        }
    }
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.dataApi)
    // Hilt
    ksp (libs.hilt.compiler)
    implementation (libs.hilt.android)
    implementation(libs.androidx.core.ktx)
    // Lifecycle
    implementation (libs.androidx.lifecycle.viewmodel)
    // Firebase
    implementation(platform(libs.firebase.bom))
    // Firebase Auth
    implementation(libs.firebase.auth)
    // Firestore
    implementation(libs.firebase.firestore.ktx)
    // Security
    implementation(libs.androidx.security.crypto)
    // Google Sign-In via Credential Manager (prod and local; not needed in demo)
    "prodImplementation"(libs.androidx.credentials)
    "prodImplementation"(libs.androidx.credentials.play.services.auth)
    "prodImplementation"(libs.google.googleid)
    "localImplementation"(libs.androidx.credentials)
    "localImplementation"(libs.androidx.credentials.play.services.auth)
    "localImplementation"(libs.google.googleid)
    // Test
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit.junit)
    testImplementation(libs.mockk)
}
