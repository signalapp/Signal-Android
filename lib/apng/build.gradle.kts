plugins {
  alias(conventionPlugins.plugins.signal.library)
}

android {
  namespace = "org.signal.apng"
}

dependencies {
  implementation(project(":core:util"))
  testImplementation(testLibs.junit.junit)
  testImplementation(testLibs.robolectric.robolectric)
}
