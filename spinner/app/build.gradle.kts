plugins {
  id("signal-sample-app")
  alias(libs.plugins.compose.compiler)
}

android {
  namespace = "org.signal.spinnertest"

  defaultConfig {
    applicationId = "org.signal.spinnertest"
  }
}

dependencies {
  implementation(project(":spinner"))

  implementation(libs.androidx.sqlite)
  implementation(libs.signal.android.database.sqlcipher)
}
