plugins {
  id("signal-library")
  id("kotlin-parcelize")
}

android {
  namespace = "org.signal.remoteconfig"
}

dependencies {
  api(project(":lib:network"))
  implementation(project(":core:util-jvm"))
  implementation(project(":core:serialization"))

  testImplementation(libs.kotlin.reflect)
}
