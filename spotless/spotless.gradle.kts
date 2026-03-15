/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
initscript {
    val spotlessVersion = "7.0.4"

    repositories {
        mavenCentral()
    }

    dependencies {
        classpath("com.diffplug.spotless:spotless-plugin-gradle:$spotlessVersion")
    }
}

allprojects {
        val ktlintVersion = "1.4.0"
        // Apply the Spotless plugin to all subprojects.
        apply<com.diffplug.gradle.spotless.SpotlessPlugin>()
        extensions.configure<com.diffplug.gradle.spotless.SpotlessExtension> {
            // Lint and format Kotlin files.
            kotlin {
                target("**/*.kt")
                targetExclude("**/build/**/*.kt")
                targetExclude("spotless/*.kt")
                ktlint(ktlintVersion).editorConfigOverride(
                    mapOf(
                        "android" to "true",
                    ),
                )
                licenseHeaderFile(rootProject.file("spotless/copyright.kt"))
            }
            // Format Kotlin scripts.
            format("kts") {
                target("**/*.kts")
                targetExclude("**/build/**/*.kts")
                targetExclude("spotless/*.kts")
                licenseHeaderFile(
                    rootProject.file("spotless/copyright.kts"),
                    // First line without comments.
                    "(^(?![\\/ ]\\*).*$)"
                )
            }
            // Format XML files.
            format("xml") {
                target("**/*.xml")
                targetExclude("**/build/**/*.xml")
                targetExclude("spotless/*.xml")
                licenseHeaderFile(
                    (rootProject.file("spotless/copyright.xml")),
                    // First tag that is not a comment.
                    "(<[^!?])"
                )
            }
        }
    }
