/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

plugins {
  id("signal-sample-app")
}

val signalBuildToolsVersion: String by rootProject.extra
val signalCompileSdkVersion: String by rootProject.extra
val signalTargetSdkVersion: Int by rootProject.extra
val signalMinSdkVersion: Int by rootProject.extra
val signalJavaVersion: JavaVersion by rootProject.extra
val signalKotlinJvmTarget: String by rootProject.extra

android {
  namespace = "org.thoughtcrime.video.app"
  compileSdkVersion = signalCompileSdkVersion

  defaultConfig {
    applicationId = "org.thoughtcrime.video.app"
    minSdk = 23
    targetSdk = signalTargetSdkVersion
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
    sourceCompatibility = signalJavaVersion
    targetCompatibility = signalJavaVersion
  }
  kotlinOptions {
    jvmTarget = signalKotlinJvmTarget
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
  implementation(project(":video"))
  implementation(project(":core-util"))
  implementation("androidx.work:work-runtime-ktx:2.9.1")
  implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
  implementation(libs.androidx.compose.ui.tooling.core)
  implementation(libs.androidx.compose.ui.test.manifest)
  androidTestImplementation(testLibs.junit.junit)
  androidTestImplementation(testLibs.androidx.test.runner)
  androidTestImplementation(testLibs.androidx.test.ext.junit.ktx)
}
