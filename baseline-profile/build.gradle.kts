plugins {
  id("com.android.test")
  alias(libs.plugins.jetbrains.kotlin.android)
  alias(benchmarkLibs.plugins.baselineprofile)
}

android {
  namespace = "org.signal.baselineprofile"
  compileSdk {
    version = release(36)
  }

  compileOptions {
    sourceCompatibility = JavaVersion.toVersion(libs.versions.javaVersion.get())
    targetCompatibility = JavaVersion.toVersion(libs.versions.javaVersion.get())
  }

  kotlinOptions {
    jvmTarget = libs.versions.kotlinJvmTarget.get()
  }

  defaultConfig {
    minSdk = 28
    targetSdk = 36

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes {
    create("mocked") {
      matchingFallbacks += "debug"
      isDebuggable = true
    }
  }

  targetProjectPath = ":Signal-Android"

  flavorDimensions += listOf("distribution", "environment")
  productFlavors {
    create("play") { dimension = "distribution" }
    create("prod") { dimension = "environment" }
  }

  testOptions {
    managedDevices {
      localDevices {
        create("api31") {
          device = "Pixel 3"
          apiLevel = 31
          systemImageSource = "aosp"
          require64Bit = false
        }
      }
    }
  }
}

baselineProfile {
  managedDevices += "api31"
  useConnectedDevices = false
}

dependencies {
  implementation(benchmarkLibs.androidx.test.ext.junit)
  implementation(benchmarkLibs.espresso.core)
  implementation(benchmarkLibs.uiautomator)
  implementation(benchmarkLibs.androidx.benchmark.macro)
}

androidComponents {
  beforeVariants(selector().all()) {
    if (it.flavorName != "playProd" && it.buildType != "mocked") {
      it.enable = false
    }
  }
}
