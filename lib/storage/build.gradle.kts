/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

plugins {
  id("signal-library")
  id("com.squareup.wire")
}

android {
  namespace = "org.signal.storage"

  testFixtures {
    enable = true
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

dependencies {
  implementation(project(":core:util"))
  implementation(project(":core:util-jvm"))

  implementation(libs.signal.android.database.sqlcipher)
  implementation(libs.androidx.sqlite)
  implementation(libs.androidx.preference)
  implementation(libs.kotlinx.coroutines.core)

  testFixturesImplementation(libs.androidx.annotation)
}
