plugins {
  id("signal-library")
}

android {
  namespace = "org.signal.devicetransfer"
}

dependencies {
  implementation(project(":core:util"))
  implementation(libs.libsignal.android)

  testImplementation(testLibs.robolectric.robolectric) {
    exclude(group = "com.google.protobuf", module = "protobuf-java")
  }
  testImplementation(testFixtures(project(":lib:libsignal-service")))
}
