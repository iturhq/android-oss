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
    namespace = "com.nohex.itur.core.data"
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
        create("demo") {
            dimension = "environment"
        }
        create("prod") {
            dimension = "environment"
        }
    }
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.model)
    implementation(projects.core.datastore)
    implementation(project(":core:datastore-proto-jvm"))

    // Hilt
    implementation(libs.hilt.android)
    implementation(libs.firebase.auth)
    implementation(libs.androidx.core.ktx)
    ksp(libs.hilt.compiler)
    // Firestore
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore.ktx)
    // Runtime
    implementation(platform(libs.androidx.compose.bom))
    implementation (libs.androidx.runtime)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit.junit)
    testImplementation(libs.mockk)
}
