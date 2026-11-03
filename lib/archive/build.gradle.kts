plugins {
  id("signal-library")
  id("com.squareup.wire")
}

android {
  namespace = "org.signal.archive"
}

wire {
  kotlin {
    javaInterop = true
  }

  sourcePath {
    srcDir("src/main/protowire")
  }
}

dependencies {
  implementation(project(":core:util"))

  implementation(libs.libsignal.android)
  implementation(libs.google.guava.android)
}
