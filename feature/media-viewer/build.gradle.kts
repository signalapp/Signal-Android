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
  namespace = "org.signal.mediaviewer"

  buildFeatures {
    compose = true
  }

  testOptions {
    unitTests {
      isIncludeAndroidResources = true

      // The Robolectric + Compose screen tests exhaust the default test worker heap.
      all { test ->
        test.maxHeapSize = "4g"
      }
    }
  }
}

dependencies {
  lintChecks(project(":lintchecks"))
  ktlintRuleset(libs.ktlint.twitter.compose)

  // Project dependencies
  implementation(project(":core:ui"))
  implementation(project(":core:util"))
  implementation(project(":core:models"))
  implementation(project(":lib:glide"))
  implementation(project(":lib:ui-components"))
  implementation(project(":lib:video"))

  // Compose BOM
  platform(libs.androidx.compose.bom).let { composeBom ->
    implementation(composeBom)
    androidTestImplementation(composeBom)
  }

  // Compose dependencies
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui.tooling.preview)
  debugImplementation(libs.androidx.compose.ui.tooling.core)

  // Lifecycle
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.ktx)

  // Media
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.ui)
  implementation(libs.lottie.compose)

  // Testing
  testImplementation(testFixtures(project(":core:ui")))
  testImplementation(project(":lib:emoji"))
  testImplementation(testLibs.junit.junit)
  testImplementation(testLibs.mockk)
  testImplementation(testLibs.assertk)
  testImplementation(testLibs.kotlinx.coroutines.test)
  testImplementation(testLibs.robolectric.robolectric)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
}
