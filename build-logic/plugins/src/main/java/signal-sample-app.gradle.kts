@file:Suppress("UnstableApiUsage")

import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.accessors.dm.LibrariesForTestLibs
import org.gradle.api.JavaVersion
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.provideDelegate
import org.gradle.kotlin.dsl.the

val libs = the<LibrariesForLibs>()
val testLibs = the<LibrariesForTestLibs>()

val signalBuildToolsVersion: String by rootProject.extra
val signalCompileSdkVersion: String by rootProject.extra
val signalTargetSdkVersion: Int by rootProject.extra
val signalMinSdkVersion: Int by rootProject.extra
val signalJavaVersion: JavaVersion by rootProject.extra
val signalKotlinJvmTarget: String by rootProject.extra

plugins {
  id("com.android.application")
  id("kotlin-android")
  id("ktlint")
}

android {
  buildToolsVersion = signalBuildToolsVersion
  compileSdkVersion = signalCompileSdkVersion

  defaultConfig {
    versionCode = 1
    versionName = "1.0"

    minSdk = signalMinSdkVersion
    targetSdk = signalTargetSdkVersion
  }

  compileOptions {
    isCoreLibraryDesugaringEnabled = true
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
}

dependencies {
  coreLibraryDesugaring(libs.android.tools.desugar)

  implementation(project(":core-util"))

  coreLibraryDesugaring(libs.android.tools.desugar)

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.fragment.ktx)
  implementation(libs.androidx.annotation)
  implementation(libs.androidx.appcompat)
  implementation(libs.rxjava3.rxandroid)
  implementation(libs.rxjava3.rxjava)
  implementation(libs.rxjava3.rxkotlin)
  implementation(libs.material.material)
  implementation(libs.androidx.constraintlayout)
  implementation(libs.kotlin.stdlib.jdk8)
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.compose.material3)

  ktlintRuleset(libs.ktlint.twitter.compose)

  testImplementation(testLibs.junit.junit)
  testImplementation(testLibs.mockito.core)
  testImplementation(testLibs.mockito.android)
  testImplementation(testLibs.mockito.kotlin)
  testImplementation(testLibs.robolectric.robolectric)
  testImplementation(testLibs.androidx.test.core)
  testImplementation(testLibs.androidx.test.core.ktx)
}
