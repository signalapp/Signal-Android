plugins {
  id("signal-sample-app")
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlinx.serialization)
}

android {
  namespace = "org.signal.registration.sample"

  defaultConfig {
    applicationId = "org.signal.registration.sample"
    versionCode = 1
    versionName = "1.0"

    minSdk = 26
    targetSdk = 34
  }

  buildFeatures {
    compose = true
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
}

dependencies {
  lintChecks(project(":lintchecks"))

  // Registration library
  implementation(project(":registration"))

  // Core dependencies
  implementation(project(":core-ui"))
  implementation(project(":core-util"))
  implementation(project(":core-models"))
  implementation(project(":libsignal-service"))

  // libsignal-protocol for PreKeyCollection types
  implementation(libs.libsignal.client)

  // Kotlin serialization for JSON parsing
  implementation(libs.kotlinx.serialization.json)

  // AndroidX
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.core.ktx)

  // Lifecycle
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Kotlinx Serialization
  implementation(libs.kotlinx.serialization.json)

  // Navigation 3
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.navigation3.ui)

  // Compose BOM
  platform(libs.androidx.compose.bom).let { composeBom ->
    implementation(composeBom)
  }

  // Compose dependencies
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui.tooling.preview)
  debugImplementation(libs.androidx.compose.ui.tooling.core)
}
