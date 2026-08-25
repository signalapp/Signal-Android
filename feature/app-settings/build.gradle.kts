plugins {
  id("signal-library")
  alias(libs.plugins.compose.compiler)
}

android {
  namespace = "org.signal.appsettings"

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

  // Project dependencies
  implementation(project(":core:ui"))
  implementation(project(":core:util"))

  // Compose BOM
  implementation(platform(libs.androidx.compose.bom))

  // Compose dependencies
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui.tooling.preview)
  debugImplementation(libs.androidx.compose.ui.tooling.core)

  // Testing
  testImplementation(testLibs.junit.junit)
  testImplementation(testLibs.assertk)
  testImplementation(testLibs.robolectric.robolectric)
  testImplementation(libs.androidx.compose.ui.test.junit4)

  // Supplies the ComponentActivity that createComposeRule() launches the screen into
  debugImplementation(libs.androidx.compose.ui.test.manifest)
}
