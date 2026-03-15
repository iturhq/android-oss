/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

// Read properties from `local.properties`.
import java.util.Properties

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}
val maptilerApiKey: String = localProperties.getProperty("MAPTILER_API_KEY", "")

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
    namespace = "com.nohex.itur.feature.map"
    compileSdk = 36

    buildFeatures.buildConfig = true

    defaultConfig {
        buildConfigField("String", "MAPTILER_API_KEY", "\"$maptilerApiKey\"")
        testInstrumentationRunner = "com.nohex.itur.feature.map.HiltTestRunner"
    }

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
        create("demo") {
            dimension = "environment"
        }
    }
}

dependencies {
    // Feature
    implementation(projects.core.domain)
    implementation(projects.core.model)
    implementation(projects.core.data)
    implementation(projects.core.auth)
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
    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)
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

    // Android instrumented tests
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
    debugImplementation(libs.androidx.ui.test.manifest)
}