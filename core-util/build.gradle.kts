plugins {
  id("signal-library")
  id("com.squareup.wire")
}

android {
  namespace = "org.signal.core.util"
}

dependencies {
  api(project(":core-util-jvm"))

  implementation(libs.androidx.sqlite)
  implementation(libs.androidx.documentfile)
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
