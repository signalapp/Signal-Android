plugins {
  id("org.jetbrains.kotlin.jvm")
  id("java-library")
  id("org.jlleitschuh.gradle.ktlint") version "12.1.1"
}

val signalJavaVersion: JavaVersion by rootProject.extra
val signalKotlinJvmTarget: String by rootProject.extra

java {
  sourceCompatibility = signalJavaVersion
  targetCompatibility = signalJavaVersion
}

kotlin {
  jvmToolchain {
    languageVersion = JavaLanguageVersion.of(signalKotlinJvmTarget)
  }
}

// NOTE: For now, in order to run ktlint on this project, you have to manually run ./gradlew :build-logic:tools:ktlintFormat
//       Gotta figure out how to get it auto-included in the normal ./gradlew ktlintFormat
ktlint {
  version.set("1.2.1")
}

dependencies {
  implementation(gradleApi())

  implementation(libs.dnsjava)
  testImplementation(testLibs.junit.junit)
  testImplementation(testLibs.mockk)
}
