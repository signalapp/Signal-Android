plugins {
  id("signal-library")
  alias(libs.plugins.compose.compiler)
}

android {
  namespace = "org.signal.uicomponents"

  buildFeatures {
    compose = true
  }
}

dependencies {
  lintChecks(project(":lintchecks"))

  api(project(":core:ui"))

  implementation(project(":lib:glide"))

  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.compose.material3)
}
