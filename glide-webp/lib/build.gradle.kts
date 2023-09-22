/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

plugins {
  id("signal-library")
  kotlin("kapt")
}

android {
  namespace = "org.signal.glide.webp"

  defaultConfig {
    externalNativeBuild {
      cmake {
        cppFlags("-std=c++17", "-fvisibility=hidden")
        arguments("-DLIBWEBP_PATH=$rootDir/libwebp")
      }
    }
  }

  externalNativeBuild {
    cmake {
      path = file("$projectDir/src/main/cpp/CMakeLists.txt")
      version = "3.22.1"
    }
  }
}

dependencies {
  implementation(project(":core-util"))

  implementation(libs.glide.glide)
  kapt(libs.glide.compiler)
}
