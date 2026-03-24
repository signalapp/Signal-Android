plugins {
  id("signal-library")
}

android {
  namespace = "org.signal.paging"
}

dependencies {
  implementation(project(":core:util"))
  implementation(libs.kotlinx.coroutines.core)
}
