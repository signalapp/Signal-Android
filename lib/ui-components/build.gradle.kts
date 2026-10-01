plugins {
  id("signal-library")
  alias(libs.plugins.compose.compiler)
}

android {
  namespace = "org.signal.uicomponents"

  buildFeatures {
    compose = true
  }

  testOptions {
    unitTests {
      isIncludeAndroidResources = true
    }
  }
}

dependencies {
  lintChecks(project(":lintchecks"))

  api(project(":core:ui"))

  implementation(project(":lib:glide"))

  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.compose.material3)

  // Testing
  testImplementation(testLibs.junit.junit)
  testImplementation(testLibs.assertk)
  testImplementation(testLibs.kotlinx.coroutines.test)
  testImplementation(testLibs.robolectric.robolectric)
  testImplementation(libs.androidx.compose.ui.test.junit4)

  // Supplies the ComponentActivity that createComposeRule() launches the screen into
  debugImplementation(libs.androidx.compose.ui.test.manifest)
}
