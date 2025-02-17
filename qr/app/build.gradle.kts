plugins {
  id("signal-sample-app")
  alias(libs.plugins.compose.compiler)
}

android {
  namespace = "org.signal.qrtest"

  defaultConfig {
    applicationId = "org.signal.qrtest"
  }
}

dependencies {
  implementation(project(":qr"))

  implementation(libs.google.zxing.android.integration)
  implementation(libs.google.zxing.core)
}
