plugins {
  id("signal-sample-app")
  alias(libs.plugins.compose.compiler)
}

android {
  namespace = "org.signal.apng.demo"

  defaultConfig {
    applicationId = "org.signal.apng"
  }
}

dependencies {
  implementation(project(":lib:apng"))
}
