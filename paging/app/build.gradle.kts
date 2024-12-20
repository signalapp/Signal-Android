plugins {
  id("signal-sample-app")
  alias(libs.plugins.compose.compiler)
}

android {
  namespace = "org.signal.pagingtest"

  defaultConfig {
    applicationId = "org.signal.pagingtest"
  }
}

dependencies {
  implementation(project(":paging"))
}
