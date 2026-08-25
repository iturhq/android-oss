/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

plugins {
    alias(libs.plugins.android.library)
    // Hilt
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    // Feature
    alias(libs.plugins.kotlin.serialization)
    // Compose
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "cat.itur.app.feature.map"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        multiDexEnabled = true
        testInstrumentationRunner = "cat.itur.app.feature.map.HiltTestRunner"
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
    }

    buildTypes {
        debug {
            enableUnitTestCoverage = true
        }
    }

    testOptions {
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
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
    // Feature
    implementation(projects.core.domain)
    implementation(projects.core.model)
    implementation(projects.core.dataApi)
    implementation(projects.core.ui)
    implementation(projects.core.location)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.foundation.android)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.ui.tooling.preview.android)
    implementation(libs.androidx.material3)

    // Hilt
    implementation(libs.hilt.android)
    implementation(libs.androidx.runtime.livedata)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.core.ktx)
    debugImplementation(libs.ui.tooling)
    ksp(libs.hilt.compiler)

    // JSON serialisation
    implementation(libs.kotlinx.serialization.json)

    // Maps
    implementation(libs.android.maplibre)

    // QR scan
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.extensions)
    implementation(libs.androidx.camera.video)
    implementation(libs.barcode.scanning)
    implementation(libs.guava)

    // QR creation
    implementation(libs.zxing.android.embedded)

    // Test
    testImplementation(libs.junit.junit)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    // Fakes for the repository contracts, demo flavor only.
    "testDemoImplementation"(projects.core.dataFake)

    // Android instrumented tests
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestUtil(libs.androidx.test.orchestrator)
    "androidTestDemoImplementation"(projects.core.dataFake)
    kspAndroidTest(libs.hilt.compiler)
    debugImplementation(libs.androidx.ui.test.manifest)
}
