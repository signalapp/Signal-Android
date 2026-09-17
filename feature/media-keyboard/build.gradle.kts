/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

plugins {
  id("signal-library")
  alias(libs.plugins.compose.compiler)
}

android {
  namespace = "org.signal.mediakeyboard"

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

  // Signal Core
  implementation(project(":core:util"))
  implementation(project(":core:ui"))
  implementation(project(":lib:glide"))

  // Compose BOM
  platform(libs.androidx.compose.bom).let { composeBom ->
    implementation(composeBom)
    androidTestImplementation(composeBom)
  }

  // Compose dependencies
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material.icons.extended)
  debugImplementation(libs.androidx.compose.ui.tooling.core)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Core AndroidX
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.activity.compose)

  // Lifecycle
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Media3 for gif (mp4) playback
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.ui.compose)

  // Rendering custom emoji drawables
  implementation(libs.accompanist.drawablepainter)

  // Testing
  testImplementation(testLibs.junit.junit)
  testImplementation(testLibs.mockk)
  testImplementation(testLibs.assertk)
  testImplementation(testLibs.kotlinx.coroutines.test)
  testImplementation(testLibs.robolectric.robolectric)
  androidTestImplementation(testLibs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
