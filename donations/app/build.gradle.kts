plugins {
  id("signal-sample-app")
  alias(libs.plugins.compose.compiler)
}

android {
  namespace = "org.signal.donations.app"

  defaultConfig {
    applicationId = "org.signal.donations.app"
  }
}

dependencies {
  implementation(project(":donations"))
  implementation(project(":core-util"))
}
