/*
 * Copyright 2023 Signal Messenger, LLC
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
  dependsOn(":core-util-jvm:generateMainProtos")
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
  implementation(libs.kotlin.reflect)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.core.jvm)
  implementation(libs.google.libphonenumber)
  implementation(libs.rxjava3.rxjava)
  implementation(libs.rxjava3.rxkotlin)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.libsignal.client)

  testImplementation(testLibs.junit.junit)
  testImplementation(testLibs.assertk)
  testImplementation(testLibs.kotlinx.coroutines.test)
}
