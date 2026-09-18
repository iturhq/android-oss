/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.gms) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.detekt) apply false
}

tasks.withType<Test> {
    useJUnitPlatform()
}

subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")
    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        allRules = false
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        baseline = project.file("detekt-baseline.xml")
        parallel = true
    }
    tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        reports {
            xml.required.set(true)
            html.required.set(true)
            txt.required.set(false)
            sarif.required.set(false)
            md.required.set(false)
        }
    }
    // Register a no-op `testClasses` task for Android modules so AS delegation doesn't fail.
    plugins.withId("com.android.application") { tasks.register("testClasses") }
    plugins.withId("com.android.library") { tasks.register("testClasses") }
    plugins.withId("org.jetbrains.kotlin.jvm") {
        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
            compilerOptions.jvmTarget = JvmTarget.JVM_17
        }
    }
    plugins.withId("com.android.library") {
        extensions.configure<com.android.build.api.dsl.LibraryExtension> {
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
        }
    }
    plugins.withId("com.android.application") {
        extensions.configure<com.android.build.api.dsl.ApplicationExtension> {
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
        }
    }
}

val expectedUnitCoverageReports = listOf(
    "core:auth (local)" to "core/auth/build/reports/coverage/test/local/debug/report.xml",
    "core:data-api" to "core/data-api/build/reports/coverage/test/debug/report.xml",
    "core:data-firebase" to "core/data-firebase/build/reports/coverage/test/debug/report.xml",
    "core:datastore" to "core/datastore/build/reports/coverage/test/debug/report.xml",
    "core:domain" to "core/domain/build/reports/coverage/test/debug/report.xml",
    "core:location" to "core/location/build/reports/coverage/test/debug/report.xml",
    "core:model" to "core/model/build/reports/coverage/test/debug/report.xml",
    "core:ui" to "core/ui/build/reports/coverage/test/debug/report.xml",
    "feature:map" to "feature/map/build/reports/coverage/test/debug/report.xml",
)

val verifyUnitCoverageReports = tasks.register("verifyUnitCoverageReports") {
    group = "verification"
    description = "Fails if a required unit coverage XML report is missing or empty and writes its aggregate."
    notCompatibleWithConfigurationCache("Parses XML coverage reports at execution time.")

    val reportFiles = expectedUnitCoverageReports.map { (_, path) -> rootProject.file(path) }
    val summary = layout.buildDirectory.file("reports/coverage/unit-test-summary.txt")
    inputs.files(reportFiles)
    outputs.file(summary)

    doLast {
        val missingReports = expectedUnitCoverageReports.filter { (_, path) ->
            rootProject.file(path).let { !it.isFile || it.length() == 0L }
        }
        if (missingReports.isNotEmpty()) {
            throw GradleException(
                "Required unit coverage report(s) missing or empty: " +
                    missingReports.joinToString { (module, path) -> "$module ($path)" },
            )
        }

        val documentBuilder = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        }.newDocumentBuilder()
        val moduleCounters = expectedUnitCoverageReports.map { (module, path) ->
            val report = documentBuilder.parse(rootProject.file(path)).documentElement
            fun counter(type: String) = (0 until report.childNodes.length)
                .map { report.childNodes.item(it) }
                .filter { node -> node.nodeName == "counter" }
                .map { node -> node.attributes }
                .firstOrNull { attributes -> attributes.getNamedItem("type").nodeValue == type }
                ?.let { attributes ->
                    attributes.getNamedItem("missed").nodeValue.toLong() to
                        attributes.getNamedItem("covered").nodeValue.toLong()
                }
                ?: throw GradleException("$module coverage report has no $type counter: $path")
            module to (counter("LINE") to counter("BRANCH"))
        }
        fun formatCounter(counter: Pair<Long, Long>): String {
            val (missed, covered) = counter
            val total = missed + covered
            val percent = if (total == 0L) 100.0 else covered * 100.0 / total
            return "$covered/$total (${String.format(java.util.Locale.ROOT, "%.2f", percent)}%)"
        }
        val totalLines = moduleCounters.fold(0L to 0L) { total, entry ->
            total.first + entry.second.first.first to total.second + entry.second.first.second
        }
        val totalBranches = moduleCounters.fold(0L to 0L) { total, entry ->
            total.first + entry.second.second.first to total.second + entry.second.second.second
        }
        summary.get().asFile.apply {
            parentFile.mkdirs()
            writeText(
                buildString {
                    appendLine("Android OSS unit coverage aggregate")
                    appendLine("module | lines | branches")
                    moduleCounters.forEach { (module, counters) ->
                        appendLine("$module | ${formatCounter(counters.first)} | ${formatCounter(counters.second)}")
                    }
                    appendLine("TOTAL | ${formatCounter(totalLines)} | ${formatCounter(totalBranches)}")
                },
            )
        }
        logger.lifecycle("Unit coverage aggregate written to ${summary.get().asFile}")
    }
}

tasks.register("unitTestCoverage") {
    group = "verification"
    description = "Runs unit tests and generates every available production coverage report."
    dependsOn(
        ":app:testProdDebugUnitTest",
        ":core:auth:createLocalDebugUnitTestCoverageReport",
        ":core:data-api:createDebugUnitTestCoverageReport",
        ":core:data-fake:testDebugUnitTest",
        ":core:data-firebase:createDebugUnitTestCoverageReport",
        ":core:datastore:createDebugUnitTestCoverageReport",
        ":core:domain:createDebugUnitTestCoverageReport",
        ":core:location:createDebugUnitTestCoverageReport",
        ":core:model:createDebugUnitTestCoverageReport",
        ":core:ui:createDebugUnitTestCoverageReport",
        ":feature:map:createDebugUnitTestCoverageReport",
    )
    finalizedBy(verifyUnitCoverageReports)
}
