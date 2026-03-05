plugins {
  id("signal-library")
  alias(libs.plugins.compose.compiler)
}

android {
  namespace = "org.signal.camera"

  buildFeatures {
    compose = true
  }
}

dependencies {
  lintChecks(project(":lintchecks"))

  // Signal Core
  implementation(project(":core:util-jvm"))
  implementation(project(":core:ui"))
  implementation(project(":lib:glide"))

  // Compose BOM
  platform(libs.androidx.compose.bom).let { composeBom ->
    implementation(composeBom)
    androidTestImplementation(composeBom)
  }

  // Compose dependencies
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material.icons.extended)
  debugImplementation(libs.androidx.compose.ui.tooling.core)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Core AndroidX
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.activity.compose)

  // Lifecycle
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // CameraX
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.video)
  implementation(libs.androidx.camera.compose)

  // QR Code scanning
  implementation(libs.google.zxing.core)

  // Testing
  testImplementation(testLibs.junit.junit)
  androidTestImplementation(testLibs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
