@file:Suppress("UnstableApiUsage")

plugins {
  id("com.android.library")
  id("androidx.benchmark")
  id("org.jetbrains.kotlin.android")
  id("ktlint")
}

val signalBuildToolsVersion: String by rootProject.extra
val signalCompileSdkVersion: String by rootProject.extra
val signalTargetSdkVersion: Int by rootProject.extra
val signalMinSdkVersion: Int by rootProject.extra
val signalJavaVersion: JavaVersion by rootProject.extra
val signalKotlinJvmTarget: String by rootProject.extra

android {
  namespace = "org.signal.microbenchmark"
  compileSdkVersion = signalCompileSdkVersion

  compileOptions {
    isCoreLibraryDesugaringEnabled = true
    sourceCompatibility = signalJavaVersion
    targetCompatibility = signalJavaVersion
  }

  kotlinOptions {
    jvmTarget = signalKotlinJvmTarget
  }

  defaultConfig {
    minSdk = signalMinSdkVersion
    targetSdk = signalTargetSdkVersion

    testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"
  }

  testBuildType = "release"
  buildTypes {
    debug {
      // Since isDebuggable can't be modified by gradle for library modules,
      // it must be done in a manifest - see src/androidTest/AndroidManifest.xml
      isMinifyEnabled = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "benchmark-proguard-rules.pro")
    }
    release {
      isDefault = true
    }
  }
}

dependencies {
  coreLibraryDesugaring(libs.android.tools.desugar)
  lintChecks(project(":lintchecks"))

  implementation(project(":core-util"))

  // Base dependencies
  androidTestImplementation(testLibs.junit.junit)
  androidTestImplementation(benchmarkLibs.androidx.test.ext.junit)
  androidTestImplementation(benchmarkLibs.androidx.benchmark.micro)

  // Dependencies of modules being tested
  androidTestImplementation(project(":libsignal-service"))
  androidTestImplementation(libs.libsignal.android)
}
