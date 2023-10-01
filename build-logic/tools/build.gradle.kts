plugins {
  id("org.jetbrains.kotlin.jvm")
  id("java-library")
  id("org.jlleitschuh.gradle.ktlint") version "11.4.2"
}

val signalJavaVersion: JavaVersion by rootProject.extra

java {
  sourceCompatibility = signalJavaVersion
  targetCompatibility = signalJavaVersion
}

// NOTE: For now, in order to run ktlint on this project, you have to manually run ./gradlew :build-logic:tools:ktlintFormat
//       Gotta figure out how to get it auto-included in the normal ./gradlew ktlintFormat
ktlint {
  version.set("0.49.1")
}

dependencies {
  implementation(gradleApi())

  implementation(libs.dnsjava)
  testImplementation(testLibs.junit.junit)
  testImplementation(testLibs.mockk)
}
