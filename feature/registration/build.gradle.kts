plugins {
  id("signal-library")
  id("kotlin-parcelize")
  id("com.squareup.wire")
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlinx.serialization)
  alias(testLibs.plugins.compose.screenshot)
}

android {
  namespace = "org.signal.registration"

  buildFeatures {
    compose = true
    buildConfig = true
  }

  lint {
    disable += "StopShip"
  }

  testOptions {
    unitTests {
      isIncludeAndroidResources = true
    }
  }

  experimentalProperties["android.experimental.enableScreenshotTest"] = true
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

wire {
  kotlin {
    javaInterop = true
  }

  sourcePath {
    srcDir("src/main/protowire")
  }
}

dependencies {
  lintChecks(project(":lintchecks"))

  // Project dependencies
  api(project(":lib:archive"))
  implementation(project(":core:ui"))
  implementation(project(":core:util"))
  implementation(project(":core:models-jvm"))
  implementation(project(":core:serialization"))
  implementation(project(":lib:device-transfer"))
  implementation(libs.libsignal.android)

  // Compose BOM
  platform(libs.androidx.compose.bom).let { composeBom ->
    implementation(composeBom)
    androidTestImplementation(composeBom)
  }

  // Compose dependencies
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

  // Phone number formatting
  implementation(libs.google.libphonenumber)

  // Phone number hint + SMS verification code retriever
  implementation(libs.google.play.services.auth)
  implementation(libs.kotlinx.coroutines.play.services)

  // Credential Manager (password manager retrieval)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.compat)

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

  // Compose screenshot testing
  screenshotTestImplementation(testLibs.compose.screenshot.validation.api)
  screenshotTestImplementation(libs.androidx.compose.ui.tooling.core)
  screenshotTestImplementation(libs.androidx.compose.ui.tooling.preview)
}
