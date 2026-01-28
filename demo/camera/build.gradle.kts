plugins {
  id("signal-sample-app")
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlinx.serialization)
}

android {
  namespace = "org.signal.camera.demo"
  compileSdkVersion = libs.versions.compileSdk.get()

  defaultConfig {
    applicationId = "org.signal.camera.demo"
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

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

dependencies {
  // Camera feature module
  implementation(project(":feature:camera"))
  
  // Core modules
  implementation(project(":core:ui"))

  // Core AndroidX
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.fragment.ktx)
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
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Navigation 3
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.navigation3.ui)

  // Kotlinx Serialization
  implementation(libs.kotlinx.serialization.json)

  // Permissions
  implementation(libs.accompanist.permissions)

  // Image loading via Glide
  implementation(libs.glide.glide)
  implementation(project(":lib:glide"))

  // Media3 for video playback
  implementation(libs.bundles.media3)

  // Testing
  androidTestImplementation(testLibs.junit.junit)
  androidTestImplementation(testLibs.androidx.test.runner)
  androidTestImplementation(testLibs.androidx.test.ext.junit.ktx)
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
