plugins {
  id("signal-library")
}

android {
  namespace = "org.signal.billing"
}

dependencies {
  lintChecks(project(":lintchecks"))

  api(libs.android.billing)
  implementation(project(":core-util"))
}
