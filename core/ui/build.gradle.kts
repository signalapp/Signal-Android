plugins {
  id("signal-library")
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlinx.serialization)
}

android {
  namespace = "org.signal.core.ui"

  buildFeatures {
    compose = true
  }

  testFixtures {
    enable = true
  }
}

dependencies {
  lintChecks(project(":lintchecks"))

  api(project(":core:util"))

  api(platform(libs.androidx.compose.bom))
  androidTestImplementation(platform(libs.androidx.compose.bom))

  api(libs.androidx.compose.material3)
  api(libs.androidx.compose.material3.adaptive)
  api(libs.androidx.compose.material3.adaptive.layout)
  api(libs.androidx.compose.material3.adaptive.navigation)
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  api(libs.androidx.compose.ui.tooling.preview)
  api(libs.androidx.activity.compose)
  debugApi(libs.androidx.compose.ui.tooling.core)
  api(libs.androidx.fragment.compose)
  implementation(libs.kotlinx.serialization.json)
  api(libs.google.zxing.core)
  api(libs.material.material)
  api(libs.androidx.window.window)
  api(libs.accompanist.permissions)

  // JUnit is used by test fixtures
  testFixturesImplementation(testLibs.junit.junit)
}
