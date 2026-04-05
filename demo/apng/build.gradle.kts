plugins {
  id("signal-sample-app")
  alias(libs.plugins.compose.compiler)
}

android {
  namespace = "org.signal.apng"

  defaultConfig {
    applicationId = "org.signal.apng"
  }
}

dependencies {
  implementation(project(":lib:apng"))
}
