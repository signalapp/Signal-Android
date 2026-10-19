/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

plugins {
  id("signal-library")
}

android {
  namespace = "org.signal.core.database"
}

dependencies {
  api(project(":core:util-jvm"))
  api(libs.androidx.sqlite)
  implementation(project(":core:util"))
  implementation(project(":core:models-jvm"))

  testImplementation(testLibs.assertk)
  testImplementation(libs.androidx.sqlite.framework)
}
