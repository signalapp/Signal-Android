/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

plugins {
  id("signal-library")
  alias(libs.plugins.ktlint)
  alias(libs.plugins.compose.compiler)
}

ktlint {
  version.set("1.5.0")
}

android {
  namespace = "org.signal.mediaoverview"

  buildFeatures {
    compose = true
  }

  testOptions {
    unitTests {
      isIncludeAndroidResources = true
    }
  }
}

dependencies {
  lintChecks(project(":lintchecks"))
  ktlintRuleset(libs.ktlint.twitter.compose)

  // Project dependencies
  implementation(project(":core:ui"))
  implementation(project(":core:util"))
  implementation(project(":lib:blurhash"))
  implementation(project(":lib:glide"))

  // Compose BOM
  implementation(platform(libs.androidx.compose.bom))

  // Compose dependencies
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.paging.compose)
  implementation(libs.androidx.compose.ui.tooling.preview)
  debugImplementation(libs.androidx.compose.ui.tooling.core)

  // Testing
  testImplementation(testFixtures(project(":core:ui")))
  testImplementation(testLibs.junit.junit)
  testImplementation(testLibs.assertk)
  testImplementation(testLibs.robolectric.robolectric)
  testImplementation(libs.androidx.compose.ui.test.junit4)

  // Supplies the ComponentActivity that createComposeRule() launches the screen into
  debugImplementation(libs.androidx.compose.ui.test.manifest)
}
