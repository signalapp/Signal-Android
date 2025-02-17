plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.jetbrains.kotlin.android) apply false
  alias(libs.plugins.jetbrains.kotlin.jvm) apply false
  alias(libs.plugins.compose.compiler) apply false
  alias(libs.plugins.ktlint)
}

buildscript {
  repositories {
    google()
    mavenCentral()
    maven {
      url = uri("https://plugins.gradle.org/m2/")
      content {
        includeGroupByRegex("org\\.jlleitschuh\\.gradle.*")
      }
    }
  }

  dependencies {
    classpath(libs.gradle)
    classpath(libs.androidx.navigation.safe.args.gradle.plugin)
    classpath(libs.protobuf.gradle.plugin)
    classpath("com.squareup.wire:wire-gradle-plugin:4.4.3") {
      exclude(group = "com.squareup.wire", module = "wire-swift-generator")
      exclude(group = "com.squareup.wire", module = "wire-grpc-client")
      exclude(group = "com.squareup.wire", module = "wire-grpc-jvm")
      exclude(group = "com.squareup.wire", module = "wire-grpc-server-generator")
      exclude(group = "io.outfoxx", module = "swiftpoet")
    }
    classpath(libs.androidx.benchmark.gradle.plugin)
    classpath(files("$rootDir/wire-handler/wire-handler-1.0.0.jar"))
    classpath(libs.com.google.devtools.ksp.gradle.plugin)
  }
}

tasks.withType<Wrapper> {
  distributionType = Wrapper.DistributionType.ALL
}

apply(from = "$rootDir/constants.gradle.kts")

subprojects {
  if (JavaVersion.current().isJava8Compatible) {
    allprojects {
      tasks.withType<Javadoc> {
        (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
      }
    }
  }

  val skipQa = setOf("Signal-Android", "libsignal-service", "lintchecks", "benchmark", "core-util-jvm", "logging")

  if (project.name !in skipQa && !project.name.endsWith("-app")) {
    tasks.register("qa") {
      group = "Verification"
      description = "Quality Assurance. Run before pushing"
      dependsOn("clean", "testReleaseUnitTest", "lintRelease")
    }
  }
}

tasks.register("buildQa") {
  group = "Verification"
  description = "Quality Assurance for build logic."
  dependsOn(
    gradle.includedBuild("build-logic").task(":tools:test"),
    gradle.includedBuild("build-logic").task(":tools:ktlintCheck"),
    gradle.includedBuild("build-logic").task(":plugins:ktlintCheck")
  )
}

tasks.register("qa") {
  group = "Verification"
  description = "Quality Assurance. Run before pushing."
  dependsOn(
    "clean",
    "buildQa",
    ":Signal-Android:testPlayProdReleaseUnitTest",
    ":Signal-Android:lintPlayProdRelease",
    "Signal-Android:ktlintCheck",
    ":libsignal-service:test",
    ":libsignal-service:ktlintCheck",
    ":Signal-Android:assemblePlayProdRelease",
    ":Signal-Android:compilePlayProdInstrumentationAndroidTestSources",
    ":microbenchmark:compileReleaseAndroidTestSources",
    ":core-util-jvm:test",
    ":core-util-jvm:ktlintCheck"
  )
}

tasks.register("clean", Delete::class) {
  delete(rootProject.layout.buildDirectory)
}

tasks.register("format") {
  group = "Formatting"
  description = "Runs the ktlint formatter on all sources in this project and included builds"
  dependsOn(
    gradle.includedBuild("build-logic").task(":plugins:ktlintFormat"),
    gradle.includedBuild("build-logic").task(":tools:ktlintFormat"),
    *subprojects.mapNotNull { tasks.findByPath(":${it.name}:ktlintFormat") }.toTypedArray()
  )
}
