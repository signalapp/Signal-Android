plugins {
  id("signal-library")
  id("kotlin-kapt")
  id("com.squareup.wire")
}

android {
  namespace = "org.signal.core.util"
}

dependencies {
  api(project(":core-util-jvm"))

  implementation(libs.androidx.sqlite)

  testImplementation(testLibs.junit.junit)
  testImplementation(testLibs.mockito.core)
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
