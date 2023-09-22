/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

plugins {
  id("signal-sample-app")
  kotlin("kapt")
}

android {
  namespace = "org.signal.glide.webp.app"
}

dependencies {
  implementation(project(":glide-webp"))

  implementation(libs.androidx.fragment.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.compose.material3)

  implementation(libs.glide.glide)
  kapt(libs.glide.compiler)
}
