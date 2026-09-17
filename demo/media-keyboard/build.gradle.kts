/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

plugins {
  id("signal-sample-app")
  alias(libs.plugins.compose.compiler)
}

android {
  namespace = "org.signal.mediakeyboard.demo"

  defaultConfig {
    applicationId = "org.signal.mediakeyboard.demo"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    vectorDrawables {
      useSupportLibrary = true
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

dependencies {
  // Media keyboard feature module
  implementation(project(":feature:media-keyboard"))

  // Core modules
  implementation(project(":core:ui"))
  implementation(project(":core:util"))
  implementation(project(":lib:glide"))
  implementation(libs.glide.glide)

  // Core AndroidX
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.activity.compose)

  // Compose BOM
  platform(libs.androidx.compose.bom).let { composeBom ->
    implementation(composeBom)
    androidTestImplementation(composeBom)
  }

  // Compose dependencies
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui.tooling.preview)
  debugImplementation(libs.androidx.compose.ui.tooling.core)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  implementation(libs.androidx.compose.material.icons.extended)

  // Lifecycle
  implementation(libs.androidx.lifecycle.runtime.compose)

  // Testing
  androidTestImplementation(testLibs.junit.junit)
  androidTestImplementation(testLibs.androidx.test.runner)
  androidTestImplementation(testLibs.androidx.test.ext.junit.ktx)
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
