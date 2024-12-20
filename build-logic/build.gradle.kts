plugins {
  alias(libs.plugins.jetbrains.kotlin.jvm) apply false
}

buildscript {
  repositories {
    google()
    mavenCentral()
  }
}

apply(from = "${rootDir}/../constants.gradle.kts")

