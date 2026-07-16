plugins {
  id("signal-library")
  id("com.squareup.wire")
  id("kotlin-parcelize")
  alias(libs.plugins.kotlinx.serialization)
}

android {
  namespace = "org.signal.core.util"
}

dependencies {
  api(project(":core:util-jvm"))

  implementation(libs.androidx.sqlite)
  implementation(libs.androidx.documentfile)
  implementation(libs.androidx.lifecycle.process)
  implementation(libs.androidx.exifinterface)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.jackson.core)
  implementation(libs.jackson.module.kotlin)
  implementation(libs.google.libphonenumber)
  implementation(libs.google.play.services.base)
  testImplementation(libs.androidx.sqlite.framework)

  testImplementation(testLibs.junit.junit)
  testImplementation(testLibs.assertk)
  testImplementation(testLibs.robolectric.robolectric)
}

wire {
  kotlin {
    javaInterop = true
  }

  sourcePath {
    srcDir("src/main/protowire")
  }
}
