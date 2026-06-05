/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

import org.gradle.api.tasks.SourceSetContainer

plugins {
  id("java-library")
  id("org.jetbrains.kotlin.jvm")
  id("ktlint")
  id("com.squareup.wire")
}

java {
  sourceCompatibility = JavaVersion.toVersion(libs.versions.javaVersion.get())
  targetCompatibility = JavaVersion.toVersion(libs.versions.javaVersion.get())
}

kotlin {
  jvmToolchain {
    languageVersion = JavaLanguageVersion.of(libs.versions.kotlinJvmTarget.get())
  }
}

afterEvaluate {
  listOf(
    "runKtlintCheckOverMainSourceSet",
    "runKtlintFormatOverMainSourceSet"
  ).forEach { taskName ->
    tasks.named(taskName) {
      mustRunAfter(tasks.named("generateMainProtos"))
    }
  }
}

wire {
  kotlin {
    javaInterop = true
  }

  sourcePath {
    srcDir("src/main/protowire")
  }
}

tasks.runKtlintCheckOverMainSourceSet {
  dependsOn(":core:network:generateMainProtos")
}

val sourceSets = extensions.getByName("sourceSets") as SourceSetContainer
sourceSets.named("main") {
  output.dir(
    mapOf("builtBy" to tasks.named("compileKotlin")),
    "$buildDir/classes/kotlin/main"
  )
}
sourceSets.named("test") {
  output.dir(
    mapOf("builtBy" to tasks.named("compileTestKotlin")),
    "$buildDir/classes/kotlin/test"
  )
}

dependencies {
  api(libs.jackson.core)
  api(libs.jackson.module.kotlin)
  api(libs.rxjava3.rxjava)
  api(libs.square.okio)

  implementation(libs.google.jsr305)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.core.jvm)
  implementation(libs.libsignal.client)

  implementation(project(":core:util-jvm"))
  implementation(project(":core:models-jvm"))

  testImplementation(testLibs.junit.junit)
  testImplementation(testLibs.assertk)
}
