plugins {
  id("signal-library")
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlinx.serialization)
}

android {
  namespace = "org.signal.passwordmanager"

  buildFeatures {
    compose = true
  }
}

dependencies {
  lintChecks(project(":lintchecks"))

  implementation(project(":core:serialization"))
  implementation(project(":core:util"))

  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.compat)
  implementation(libs.kotlinx.serialization.json)

  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.compose.ui)

  testImplementation(testLibs.junit.junit)
  testImplementation(testLibs.assertk)
}
