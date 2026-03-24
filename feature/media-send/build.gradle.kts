plugins {
  id("signal-library")
  id("kotlin-parcelize")
  alias(libs.plugins.ktlint)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlinx.serialization)
}

ktlint {
  version.set("1.5.0")
}

android {
  namespace = "org.signal.mediasend"

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
  lintChecks(project(":lintchecks"))
  ktlintRuleset(libs.ktlint.twitter.compose)

  // Project dependencies
  implementation(libs.androidx.ui.test.junit4)
  implementation(project(":core:ui"))
  implementation(project(":core:util"))
  implementation(project(":core:models"))
  implementation(project(":lib:image-editor"))
  implementation(project(":lib:glide"))

  // Compose BOM
  platform(libs.androidx.compose.bom).let { composeBom ->
    implementation(composeBom)
    androidTestImplementation(composeBom)
  }

  // Compose dependencies
  implementation(libs.androidx.activity.compose)
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
