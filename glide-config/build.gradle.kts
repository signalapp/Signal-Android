plugins {
  id("signal-library")
}

android {
  namespace = "org.signal.glide"
}

dependencies {
  implementation(libs.glide.glide)
}
