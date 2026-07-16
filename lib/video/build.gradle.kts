plugins {
  id("signal-library")
  alias(libs.plugins.kotlinx.serialization)
}

android {
  namespace = "org.signal.video"
}

dependencies {
  implementation(project(":core:util"))
  implementation(project(":core:serialization"))
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.libsignal.android)
  implementation(libs.google.guava.android)
  implementation(libs.androidx.media3.ui)
  implementation(libs.androidx.media3.exoplayer)

  implementation(libs.bundles.mp4parser) {
    exclude(group = "junit", module = "junit")
  }
}
