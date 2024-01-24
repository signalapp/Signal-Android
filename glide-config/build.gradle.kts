plugins {
  id("signal-library")
  id("com.google.devtools.ksp")
}

android {
  namespace = "org.signal.glide"
}

dependencies {
  implementation(libs.glide.glide)
  ksp(libs.glide.ksp)
}
