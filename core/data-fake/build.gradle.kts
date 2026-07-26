/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.nohex.itur.core.data.fake"
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
    // These types appear directly in this module's own public classes' signatures
    // (TestFixtures, the Fake*Repository constructors and members).
    api(projects.core.dataApi)
    api(projects.core.domain)
    api(projects.core.model)
    implementation(libs.androidx.core.ktx)
    // Runtime (mutableStateListOf)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.runtime)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}
