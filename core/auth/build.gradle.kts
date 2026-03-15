/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
import java.util.Properties

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

plugins {
    alias(libs.plugins.android.library)
    // Hilt
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.nohex.itur.core.auth"
    compileSdk = 36

    buildFeatures.buildConfig = true

    buildTypes {
        debug {
            enableUnitTestCoverage = true
        }
    }

    flavorDimensions += "environment"
    productFlavors {
        create("prod") {
            dimension = "environment"
            buildConfigField(
                "String",
                "GOOGLE_WEB_CLIENT_ID",
                "\"${localProperties.getProperty("GOOGLE_WEB_CLIENT_ID", "")}\"",
            )
        }
        create("demo") {
            dimension = "environment"
            buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"\"")
        }
    }
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.data)
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
    // Google Sign-In via Credential Manager (prod only; not needed in demo)
    "prodImplementation"(libs.androidx.credentials)
    "prodImplementation"(libs.androidx.credentials.play.services.auth)
    "prodImplementation"(libs.google.googleid)
    // Test
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit.junit)
}