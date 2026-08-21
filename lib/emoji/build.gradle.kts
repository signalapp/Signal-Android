plugins {
  id("signal-library")
  id("com.squareup.wire")
  alias(libs.plugins.compose.compiler)
}

android {
  namespace = "org.signal.emoji"

  buildFeatures {
    compose = true
  }

  testOptions {
    unitTests {
      isIncludeAndroidResources = true
    }
  }
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
  implementation(project(":core:ui"))

  implementation(libs.jackson.module.kotlin)
  implementation(libs.androidx.emoji2)
  implementation(libs.square.okhttp3)
  implementation(libs.square.okio)
  api(libs.glide.glide)

  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.compose.material3)
  implementation(libs.accompanist.drawablepainter)

  testImplementation(testLibs.mockk)
  testImplementation(testLibs.assertk)
}
