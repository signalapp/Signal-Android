plugins {
  id("signal-sample-app")
  alias(libs.plugins.compose.compiler)
}

android {
  namespace = "org.signal.debuglogsviewer.app"

  defaultConfig {
    applicationId = "org.signal.debuglogsviewer.app"
  }
}

dependencies {
  implementation(project(":debuglogs-viewer"))
  implementation(project(":core-util"))
}
