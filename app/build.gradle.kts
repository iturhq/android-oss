/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
// Read properties from `local.properties`.
import com.google.gms.googleservices.GoogleServicesTask
import java.util.Properties

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}
val apiKey: String = localProperties.getProperty("MAPLIBRE_API_KEY") ?: ""
val maptilerApiKey: String = localProperties.getProperty("MAPTILER_API_KEY") ?: ""
val googleWebClientId: String = localProperties.getProperty("GOOGLE_WEB_CLIENT_ID") ?: ""

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // Hilt
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    // Firebase
    alias(libs.plugins.gms)
    // Observability is limited to production-backed variants.
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.firebase.perf)
    // Generates the third-party license data OssLicensesMenuActivity displays (AOSS-386B).
    alias(libs.plugins.oss.licenses)
}

android {
    namespace = "cat.itur.app"
    compileSdk = 36

    buildFeatures.buildConfig = true

    defaultConfig {
        applicationId = "cat.itur.app.oss"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "MAPLIBRE_API_KEY", "\"$apiKey\"")
        // Application-owned configuration injected into reusable modules (feature:map, core:auth)
        // via AppConfigModule, rather than those modules embedding it themselves.
        buildConfigField("String", "MAPTILER_API_KEY", "\"$maptilerApiKey\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
    }

    // With built-in Kotlin in AGP 9.0, jvmTarget defaults to compileOptions.targetCompatibility
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    flavorDimensions += "environment"
    productFlavors {
        create("prod") {
            dimension = "environment"
        }
        create("local") {
            dimension = "environment"
            applicationIdSuffix = ".local"
        }
    }
}

dependencies {
    implementation(projects.core.auth)
    implementation(projects.core.domain)
    implementation(projects.core.model)
    implementation(projects.core.data)
    implementation(projects.core.ui)
    implementation(projects.feature.map)
//    implementation(projects.feature.join)
//    implementation(projects.feature.track)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    // Jetpack Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.navigation.compose)
    // Hilt
    implementation (libs.hilt.android)
    ksp (libs.hilt.compiler)
    // Maps
    implementation(libs.android.maplibre)

    // Third-party license attribution screen (AOSS-386B).
    implementation(libs.play.services.oss.licenses)

    // Observability is linked only by the production-backed variants.
    "prodImplementation"(platform(libs.firebase.bom))
    "prodImplementation"(libs.firebase.crashlytics.ktx)
    "prodImplementation"(libs.firebase.perf.ktx)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

androidComponents {
    beforeVariants(selector().withBuildType("release").withFlavor("environment" to "local")) { variantBuilder ->
        variantBuilder.enable = false
    }
}

// Local builds supply emulator-only options from src/local/res and must never
// consume a production google-services.json.
tasks.withType<GoogleServicesTask>().configureEach {
    if (name.startsWith("processLocal")) enabled = false
}
