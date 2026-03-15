/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
plugins {
    kotlin("jvm")
    alias(libs.plugins.wire)
}

group = "com.nohex.itur.core"
version = "1.0"

java {
    // set Java version compatibility if needed
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

wire {
    sourcePath {
        srcDir("src/main/proto")
    }
    kotlin {
        out = "${project.layout.buildDirectory.get().asFile}/generated/source/wire"
        javaInterop = false
    }
}

dependencies {
    // expose Wire runtime to consumers
    api(libs.wire.runtime)
}
