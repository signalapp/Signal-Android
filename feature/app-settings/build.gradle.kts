plugins {
  id("signal-library")
  id("kotlin-parcelize")
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
  api(project(":lib:password-manager"))
  api(project(":lib:ui-components"))
  implementation(project(":core:ui"))
  implementation(project(":core:models"))
  implementation(project(":core:models-jvm"))
  implementation(project(":core:util"))
  implementation(project(":lib:signal-login"))
  implementation(project(":lib:donations"))
  implementation(project(":lib:emoji"))

  // Compose BOM
  implementation(platform(libs.androidx.compose.bom))

  // Compose dependencies
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.ktx)
  implementation(libs.androidx.compose.ui.tooling.preview)
  debugImplementation(libs.androidx.compose.ui.tooling.core)

  // Testing
  testImplementation(testLibs.junit.junit)
  testImplementation(testLibs.assertk)
  testImplementation(testLibs.mockk)
  testImplementation(testLibs.kotlinx.coroutines.test)
  testImplementation(testLibs.robolectric.robolectric)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(testFixtures(project(":core:ui")))
  testImplementation(testFixtures(project(":lib:emoji")))

  // Supplies the ComponentActivity that createComposeRule() launches the screen into
  debugImplementation(libs.androidx.compose.ui.test.manifest)
}
