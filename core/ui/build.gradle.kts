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

  composeOptions {
    kotlinCompilerExtensionVersion = "1.5.4"
  }
}

dependencies {
  lintChecks(project(":lintchecks"))

  platform(libs.androidx.compose.bom).let { composeBom ->
    api(composeBom)
    androidTestApi(composeBom)
  }

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
}
