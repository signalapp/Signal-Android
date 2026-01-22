plugins {
  id("signal-library")
  id("com.google.devtools.ksp")
  alias(libs.plugins.compose.compiler)
}

android {
  namespace = "org.signal.glide"

  buildFeatures {
    compose = true
  }
}

dependencies {
  implementation(project(":core:util"))

  implementation(libs.glide.glide)
  ksp(libs.glide.ksp)

  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.compose.material3)
  implementation(libs.accompanist.drawablepainter)
}
