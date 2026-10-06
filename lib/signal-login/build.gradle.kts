plugins {
  id("signal-library")
  alias(libs.plugins.compose.compiler)
}

android {
  namespace = "org.signal.signallogin"

  buildFeatures {
    compose = true
  }
}

dependencies {
  lintChecks(project(":lintchecks"))

  api(project(":core:ui"))
  implementation(project(":core:util-jvm"))
  implementation(project(":core:models-jvm"))
  implementation(project(":lib:password-manager"))
  implementation(libs.libsignal.android)

  implementation(libs.androidx.core.ktx)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.lifecycle.viewmodel.ktx)
}
