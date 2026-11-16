plugins {
  id("signal-library")
  alias(libs.plugins.compose.compiler)
}

android {
  namespace = "org.signal.screener"

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
  api(platform(libs.androidx.compose.bom))
  api(libs.androidx.compose.ui.test.junit4)
  api(testLibs.junit.junit)
  api(testLibs.robolectric.robolectric)

  implementation(project(":core:ui"))

  testImplementation(testLibs.assertk)
  testImplementation(libs.androidx.compose.material3)

  // Supplies the ComponentActivity that createComposeRule() launches the content into
  debugImplementation(libs.androidx.compose.ui.test.manifest)
}
