plugins {
  alias(conventionPlugins.plugins.signal.library)
}

android {
  namespace = "org.signal.video"
}

dependencies {
  implementation(project(":core:util"))
  implementation(libs.libsignal.android)
  implementation(libs.google.guava.android)
  implementation(libs.androidx.media3.ui)
  implementation(libs.androidx.media3.exoplayer)

  implementation(libs.bundles.mp4parser) {
    exclude(group = "junit", module = "junit")
  }
}
