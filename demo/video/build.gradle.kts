/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

plugins {
  id("signal-sample-app")
  alias(libs.plugins.compose.compiler)
}

android {
  namespace = "org.thoughtcrime.video.app"
  compileSdkVersion = libs.versions.compileSdk.get()

  defaultConfig {
    applicationId = "org.thoughtcrime.video.app"
    minSdk = 23
    targetSdk = libs.versions.targetSdk.get().toInt()
    versionCode = 1
    versionName = "1.0"

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
  compileOptions {
    sourceCompatibility = JavaVersion.toVersion(libs.versions.javaVersion.get())
    targetCompatibility = JavaVersion.toVersion(libs.versions.javaVersion.get())
  }
  kotlinOptions {
    jvmTarget = libs.versions.kotlinJvmTarget.get()
  }
  buildFeatures {
    compose = true
  }
  composeOptions {
    kotlinCompilerExtensionVersion = "1.5.4"
  }
  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

dependencies {
  implementation(libs.androidx.fragment.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.compose.material3)
  implementation(libs.bundles.media3)
  implementation(project(":lib:video"))
  implementation(project(":core:util"))
  implementation("androidx.work:work-runtime-ktx:2.9.1")
  implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
  implementation(libs.androidx.compose.ui.tooling.core)
  implementation(libs.androidx.compose.ui.test.manifest)
  androidTestImplementation(testLibs.junit.junit)
  androidTestImplementation(testLibs.androidx.test.runner)
  androidTestImplementation(testLibs.androidx.test.ext.junit.ktx)
}
