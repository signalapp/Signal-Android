val signalJavaVersion: JavaVersion by rootProject.extra
val signalKotlinJvmTarget: String by rootProject.extra

plugins {
  id("java-library")
  id("org.jetbrains.kotlin.jvm")
}

java {
  sourceCompatibility = signalJavaVersion
  targetCompatibility = signalJavaVersion
}

kotlin {
  jvmToolchain {
    languageVersion = JavaLanguageVersion.of(signalKotlinJvmTarget)
  }
}

dependencies {
  compileOnly(lintLibs.lint.api)
  compileOnly(lintLibs.lint.checks)

  testImplementation(lintLibs.lint.tests)
  testImplementation(lintLibs.lint.api)
  testImplementation(testLibs.junit.junit)
}

tasks.jar {
  manifest {
    attributes(
      "Lint-Registry-v2" to "org.signal.lint.Registry"
    )
  }
}
