@file:Suppress("UnstableApiUsage")

plugins {
  id("com.android.library")
  id("androidx.benchmark")
  id("org.jetbrains.kotlin.android")
  id("ktlint")
}

android {
  namespace = "org.signal.microbenchmark"
  compileSdkVersion = libs.versions.compileSdk.get()

  compileOptions {
    isCoreLibraryDesugaringEnabled = true
    sourceCompatibility = JavaVersion.toVersion(libs.versions.javaVersion.get())
    targetCompatibility = JavaVersion.toVersion(libs.versions.javaVersion.get())
  }

  kotlinOptions {
    jvmTarget = libs.versions.kotlinJvmTarget.get()
  }

  defaultConfig {
    minSdk = libs.versions.minSdk.get().toInt()
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
  implementation(project(":core-models"))

  // Base dependencies
  androidTestImplementation(testLibs.junit.junit)
  androidTestImplementation(benchmarkLibs.androidx.test.ext.junit)
  androidTestImplementation(benchmarkLibs.androidx.benchmark.micro)

  // Dependencies of modules being tested
  androidTestImplementation(project(":libsignal-service"))
  androidTestImplementation(libs.libsignal.android)
}
