/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("java-library")
  id("org.jetbrains.kotlin.jvm")
  id("java-test-fixtures")
  id("maven-publish")
  id("signing")
  id("idea")
  id("org.jlleitschuh.gradle.ktlint")
  id("com.squareup.wire")
}

val signalBuildToolsVersion: String by rootProject.extra
val signalCompileSdkVersion: String by rootProject.extra
val signalTargetSdkVersion: Int by rootProject.extra
val signalMinSdkVersion: Int by rootProject.extra
val signalJavaVersion: JavaVersion by rootProject.extra
val signalKotlinJvmTarget: String by rootProject.extra

java {
  withJavadocJar()
  withSourcesJar()
  sourceCompatibility = signalJavaVersion
  targetCompatibility = signalJavaVersion
}

tasks.withType<KotlinCompile>().configureEach {
  kotlinOptions {
    jvmTarget = signalKotlinJvmTarget
  }
}

afterEvaluate {
  listOf(
    "runKtlintCheckOverMainSourceSet",
    "runKtlintFormatOverMainSourceSet",
    "sourcesJar"
  ).forEach { taskName ->
    tasks.named(taskName) {
      mustRunAfter(tasks.named("generateMainProtos"))
    }
  }
}

ktlint {
  version.set("1.2.1")

  filter {
    exclude { entry ->
      entry.file.toString().contains("build/generated/source/wire")
    }
  }
}

wire {
  protoLibrary = true

  kotlin {
    javaInterop = true
  }

  sourcePath {
    srcDir("src/main/protowire")
  }

  custom {
    // Comes from wire-handler jar project
    schemaHandlerFactoryClass = "org.signal.wire.Factory"
  }
}

tasks.whenTaskAdded {
  if (name == "lint") {
    enabled = false
  }
}

dependencies {
  api(libs.google.libphonenumber)
  api(libs.jackson.core)
  api(libs.jackson.module.kotlin)

  implementation(libs.libsignal.client)
  api(libs.square.okhttp3)
  api(libs.square.okio)
  implementation(libs.google.jsr305)

  api(libs.rxjava3.rxjava)

  implementation(libs.kotlin.stdlib.jdk8)

  implementation(project(":core-util-jvm"))

  testImplementation(testLibs.junit.junit)
  testImplementation(testLibs.assertj.core)
  testImplementation(testLibs.conscrypt.openjdk.uber)
  testImplementation(testLibs.mockito.core)
  testImplementation(testLibs.mockk)

  testFixturesImplementation(libs.libsignal.client)
  testFixturesImplementation(testLibs.junit.junit)
}
