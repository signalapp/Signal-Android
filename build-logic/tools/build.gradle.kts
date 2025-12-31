plugins {
  alias(libs.plugins.jetbrains.kotlin.jvm)
  id("java-library")
  alias(libs.plugins.ktlint)
}

java {
  sourceCompatibility = JavaVersion.toVersion(libs.versions.javaVersion.get())
  targetCompatibility = JavaVersion.toVersion(libs.versions.javaVersion.get())
}

kotlin {
  jvmToolchain {
    languageVersion = JavaLanguageVersion.of(libs.versions.kotlinJvmTarget.get())
  }
}

// NOTE: For now, in order to run ktlint on this project, you have to manually run ./gradlew :build-logic:tools:ktlintFormat
//       Gotta figure out how to get it auto-included in the normal ./gradlew ktlintFormat
ktlint {
  version.set("1.5.0")
}

dependencies {
  implementation(gradleApi())

  implementation(libs.dnsjava)
  implementation(libs.square.okhttp3)

  testImplementation(testLibs.junit.junit)
  testImplementation(testLibs.mockk)
}
