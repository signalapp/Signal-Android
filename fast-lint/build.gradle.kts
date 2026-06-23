plugins {
  id("java-library")
  id("org.jetbrains.kotlin.jvm")
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

dependencies {
  implementation(lintLibs.intellij.core)
  implementation(lintLibs.kotlin.compiler)
  implementation(libs.google.guava.android)

  testImplementation(testLibs.junit.junit)
}

tasks.register<JavaExec>("fastLint") {
  group = "Verification"
  description = "Runs the fast custom AST linter (:fast-lint) over the whole repository. Fails on any finding."
  mainClass.set("org.signal.fastlint.Lint")
  classpath = sourceSets["main"].runtimeClasspath
  maxHeapSize = "2g"

  // Strings resolved at configuration time so the task is configuration-cache compatible.
  val repoRoot = rootProject.projectDir.absolutePath
  val reportPath = layout.buildDirectory.file("reports/fast-lint/findings.txt").get().asFile.absolutePath
  args(repoRoot, "--report=$reportPath")

  // A linter should run every time, not be skipped as up-to-date.
  outputs.upToDateWhen { false }
}
