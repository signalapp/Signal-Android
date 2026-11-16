@file:Suppress("UnstableApiUsage")

import org.gradle.accessors.dm.LibrariesForBenchmarkLibs
import org.gradle.accessors.dm.LibrariesForTestLibs
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val benchmarkLibs = the<LibrariesForBenchmarkLibs>()
val testLibs = the<LibrariesForTestLibs>()

plugins {
  id("com.android.test")
  id("ktlint")
}

/**
 * Drives Signal from outside its process, the way adb would: installs the app and its language splits with `pm`,
 * restarts it, and reads the screen. Runs in its own process (self-instrumenting), so it survives the app being killed
 * and does not need to share the app's signing key. See [org.signal.languagesplittests.LanguageSplitDevice].
 */
android {
  namespace = "org.signal.languagesplittests"

  compileSdk {
    version = release(libs.versions.compileSdk.get().toInt())
  }

  compileOptions {
    sourceCompatibility = JavaVersion.toVersion(libs.versions.javaVersion.get())
    targetCompatibility = JavaVersion.toVersion(libs.versions.javaVersion.get())
  }

  defaultConfig {
    // UiAutomation.executeShellCommandRw, which gives shell commands a stdin.
    minSdk = 31
    targetSdk = libs.versions.targetSdk.get().toInt()

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    missingDimensionStrategy("environment", "prod")
    missingDimensionStrategy("distribution", "play")
  }

  targetProjectPath = ":Signal-Android"
  experimentalProperties["android.experimental.self-instrumenting"] = true
}

kotlin {
  compilerOptions {
    jvmTarget = JvmTarget.fromTarget(libs.versions.kotlinJvmTarget.get())
  }
}

dependencies {
  // The version the rest of the build resolves, so dependency verification has it.
  implementation(libs.androidx.annotation)
  implementation(testLibs.androidx.test.runner)
  implementation(benchmarkLibs.androidx.test.ext.junit)
  implementation(benchmarkLibs.uiautomator)
  implementation(testLibs.assertk)
}
