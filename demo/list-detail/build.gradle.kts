/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

plugins {
  id("signal-sample-app")
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlinx.serialization)
}

android {
  namespace = "org.signal.listdetail.demo"

  defaultConfig {
    applicationId = "org.signal.listdetail.demo"
  }
}

dependencies {
  implementation(project(":core:ui"))

  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.navigation3.ui)

  implementation(libs.androidx.lifecycle.runtime.compose)

  implementation(libs.androidx.compose.material.icons.extended)

  implementation(libs.kotlinx.serialization.json)
}
