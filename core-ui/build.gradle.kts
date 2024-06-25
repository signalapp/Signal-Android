plugins {
  id("signal-library")
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
  api(libs.androidx.compose.ui.tooling.preview)
  debugApi(libs.androidx.compose.ui.tooling.core)
}
