plugins {
  id("signal-library")
}

android {
  namespace = "org.signal.locale"
}

dependencies {
  testImplementation(testLibs.assertk)
}
