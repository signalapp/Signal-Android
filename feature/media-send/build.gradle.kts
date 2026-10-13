plugins {
  id("signal-library")
  id("kotlin-parcelize")
  alias(libs.plugins.ktlint)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlinx.serialization)
}

ktlint {
  version.set("1.5.0")
}

android {
  namespace = "org.signal.mediasend"

  buildFeatures {
    compose = true
  }

  testOptions {
    unitTests {
      isIncludeAndroidResources = true
    }
  }
}

screenshotTests {
  // Fraction of differing pixels tolerated before a screenshot test fails (0.0001 = 0.01%).
  imageDifferenceThreshold = 0.0001f
}

// The screenshot validation task compares every reference image in a single forked JVM, which
// exhausts the default heap once a module has many previews. Give it more room.
tasks.withType<Test>().configureEach {
  if (name.contains("ScreenshotTest")) {
    maxHeapSize = "4g"
  }
}

dependencies {
  lintChecks(project(":lintchecks"))
  ktlintRuleset(libs.ktlint.twitter.compose)

  // Project dependencies
  implementation(project(":core:ui"))
  implementation(project(":core:util"))
  implementation(project(":core:models"))
  implementation(project(":lib:image-editor"))
  implementation(project(":lib:glide"))
  implementation(project(":lib:video"))
  implementation(project(":feature:camera"))

  // Compose BOM
  platform(libs.androidx.compose.bom).let { composeBom ->
    implementation(composeBom)
    androidTestImplementation(composeBom)
  }

  // Compose dependencies
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.fragment.compose)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui.tooling.preview)
  debugImplementation(libs.androidx.compose.ui.tooling.core)

  // Navigation 3
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.navigation3.ui)

  // Kotlinx Serialization
  implementation(libs.kotlinx.serialization.json)

  // Lifecycle
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // Permissions
  implementation(libs.accompanist.permissions)

  // Media
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.ui)

  // CameraX
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.compose)

  // Testing
  testImplementation(testFixtures(project(":core:ui")))
  testImplementation(testLibs.junit.junit)
  testImplementation(testLibs.mockk)
  testImplementation(testLibs.assertk)
  testImplementation(testLibs.kotlinx.coroutines.test)
  testImplementation(testLibs.robolectric.robolectric)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(testLibs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
}
