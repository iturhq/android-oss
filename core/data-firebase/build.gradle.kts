/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.nohex.itur.core.data.firebase"
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
}

dependencies {
    implementation(projects.core.dataApi)
    implementation(projects.core.domain)
    implementation(projects.core.model)
    implementation(projects.core.datastore)
    implementation(project(":core:datastore-proto-jvm"))

    // For the javax.inject.Inject annotation used by the @Inject constructors below --
    // Dagger/Hilt component assembly (and its own annotation processing) happens in
    // whichever application module actually builds the Hilt graph, not here.
    implementation(libs.hilt.android)
    implementation(libs.androidx.core.ktx)
    // Firestore
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore.ktx)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit.junit)
    testImplementation(libs.mockk)
}
