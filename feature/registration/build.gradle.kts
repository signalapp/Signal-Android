plugins {
  id("signal-library")
  id("kotlin-parcelize")
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlinx.serialization)
}

android {
  namespace = "org.signal.registration"

  buildFeatures {
    compose = true
  }

  testOptions {
    unitTests {
      isIncludeAndroidResources = true
    }
  }
}

dependencies {
  implementation(libs.androidx.ui.test.junit4)
  lintChecks(project(":lintchecks"))

  // Project dependencies
  implementation(project(":core:ui"))
  implementation(project(":core:util"))
  implementation(project(":core:models"))
  implementation(libs.libsignal.android)

  // Compose BOM
  platform(libs.androidx.compose.bom).let { composeBom ->
    implementation(composeBom)
    androidTestImplementation(composeBom)
  }

  // Compose dependencies
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui.tooling.preview)
  debugImplementation(libs.androidx.compose.ui.tooling.core)

  // Navigation 3
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.navigation3.ui)

  // Kotlinx Serialization
  implementation(libs.kotlinx.serialization.json)

  // Lifecycle
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // Permissions
  implementation(libs.accompanist.permissions)

  // Phone number formatting
  implementation(libs.google.libphonenumber)

  // Testing
  testImplementation(testLibs.junit.junit)
  testImplementation(testLibs.mockk)
  testImplementation(testLibs.assertk)
  testImplementation(testLibs.kotlinx.coroutines.test)
  testImplementation(testLibs.robolectric.robolectric)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(testLibs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  implementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
}
