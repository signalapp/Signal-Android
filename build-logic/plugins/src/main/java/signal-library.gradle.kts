@file:Suppress("UnstableApiUsage")

import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.accessors.dm.LibrariesForTestLibs
import org.gradle.api.JavaVersion

val libs = the<LibrariesForLibs>()
val testLibs = the<LibrariesForTestLibs>()

plugins {
  // We cannot use the version catalog in the plugins block in convention plugins (it's not supported).
  // Instead, plugin versions are controlled through the dependencies block in the build.gradle.kts.
  id("com.android.library")
  id("kotlin-android")
  id("ktlint")
}

android {
  buildToolsVersion = libs.versions.buildTools.get()
  compileSdkVersion = libs.versions.compileSdk.get()

  defaultConfig {
    minSdk = libs.versions.minSdk.get().toInt()
    vectorDrawables.useSupportLibrary = true
  }

  compileOptions {
    isCoreLibraryDesugaringEnabled = true
    sourceCompatibility = JavaVersion.toVersion(libs.versions.javaVersion.get())
    targetCompatibility = JavaVersion.toVersion(libs.versions.javaVersion.get())
  }

  kotlinOptions {
    jvmTarget = libs.versions.kotlinJvmTarget.get()
    suppressWarnings = true
  }

  lint {
    disable += "InvalidVectorPath"
  }
}

dependencies {
  lintChecks(project(":lintchecks"))

  coreLibraryDesugaring(libs.android.tools.desugar)

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.fragment.ktx)
  implementation(libs.androidx.annotation)
  implementation(libs.androidx.appcompat)
  implementation(libs.rxjava3.rxandroid)
  implementation(libs.rxjava3.rxjava)
  implementation(libs.rxjava3.rxkotlin)
  implementation(libs.kotlin.stdlib.jdk8)

  ktlintRuleset(libs.ktlint.twitter.compose)

  testImplementation(testLibs.junit.junit)
  testImplementation(testLibs.robolectric.robolectric)
  testImplementation(testLibs.androidx.test.core)
  testImplementation(testLibs.androidx.test.core.ktx)
}
