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
    namespace = "cat.itur.app.core.data"
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
    }

}

dependencies {
    // Production and local use distinct Firebase endpoints (local targets emulators).
    implementation(projects.core.dataApi)
    "prodImplementation"(projects.core.dataFirebase)
    "localImplementation"(projects.core.dataFirebase)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    // Firestore (this module's own DataModule constructs the FirebaseFirestore instance)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.firebase.functions)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit.junit)
    testImplementation(libs.mockk)
}
