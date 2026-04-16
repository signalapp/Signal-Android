/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

plugins {
  id("java-library")
  id("org.jetbrains.kotlin.jvm")
  alias(libs.plugins.kotlinx.serialization)
  id("ktlint")
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

dependencies {
  implementation(project(":core:util-jvm"))
  implementation(project(":core:models-jvm"))

  implementation(libs.kotlinx.serialization.json)
  implementation(libs.libsignal.client)
}
