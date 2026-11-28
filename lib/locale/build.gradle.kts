plugins {
  id("signal-library")
}

android {
  namespace = "org.signal.locale"
}

dependencies {
  implementation(project(":core:util"))

  api(libs.google.play.feature.delivery)
  implementation(libs.google.play.services.base)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.play.services)

  testImplementation(testLibs.assertk)
  testImplementation(testLibs.mockk)
  testImplementation(testLibs.kotlinx.coroutines.test)
}
