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

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

  androidTestImplementation(testLibs.androidx.test.core)
  androidTestImplementation(testLibs.androidx.test.core.ktx)
  androidTestImplementation(testLibs.androidx.test.ext.junit)
  androidTestImplementation(testLibs.androidx.test.ext.junit.ktx)
  androidTestImplementation(testLibs.androidx.test.monitor)
  androidTestImplementation(testLibs.androidx.test.runner)
}
