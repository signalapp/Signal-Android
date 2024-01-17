import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val signalKotlinJvmTarget: String by rootProject.extra

buildscript {
  rootProject.extra["kotlin_version"] = "1.8.10"
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
    classpath("com.android.tools.build:gradle:8.0.2")
    classpath("androidx.navigation:navigation-safe-args-gradle-plugin:2.5.3")
    classpath("com.google.protobuf:protobuf-gradle-plugin:0.9.0")
    classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${rootProject.extra["kotlin_version"] as String}")
    classpath(libs.ktlint)
    classpath("app.cash.exhaustive:exhaustive-gradle:0.1.1")
    classpath("com.squareup.wire:wire-gradle-plugin:4.4.3") {
      exclude(group = "com.squareup.wire", module = "wire-swift-generator")
      exclude(group = "com.squareup.wire", module = "wire-grpc-client")
      exclude(group = "com.squareup.wire", module = "wire-grpc-jvm")
      exclude(group = "com.squareup.wire", module = "wire-grpc-server-generator")
      exclude(group = "io.outfoxx", module = "swiftpoet")
    }
    classpath("androidx.benchmark:benchmark-gradle-plugin:1.1.0-beta04")
    classpath(files("$rootDir/wire-handler/wire-handler-1.0.0.jar"))
  }
}

tasks.withType<Wrapper> {
  distributionType = Wrapper.DistributionType.ALL
}

apply(from = "${rootDir}/constants.gradle.kts")

allprojects {
  // Needed because otherwise the kapt task defaults to jvmTarget 17, which "poisons the well" and requires us to bump up too
  tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
      jvmTarget = signalKotlinJvmTarget
    }
  }
}

subprojects {
  if (JavaVersion.current().isJava8Compatible()) {
    allprojects {
      tasks.withType<Javadoc> {
        (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
      }
    }
  }

  val skipQa = setOf("Signal-Android", "libsignal-service", "lintchecks", "benchmark", "core-util-jvm", "logging")

  if (!skipQa.contains(project.name) && !project.name.endsWith("-app")) {
    task("qa") {
      group = "Verification"
      description = "Quality Assurance. Run before pushing"
      dependsOn("clean", "testReleaseUnitTest", "lintRelease")
    }
  }
}

task("buildQa") {
  group = "Verification"
  description = "Quality Assurance for build logic."
  dependsOn(
    gradle.includedBuild("build-logic").task(":tools:test"),
    gradle.includedBuild("build-logic").task(":tools:ktlintCheck"),
    gradle.includedBuild("build-logic").task(":plugins:ktlintCheck")
  )
}

task("qa") {
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
  delete(rootProject.buildDir)
}

task("format") {
  group = "Formatting"
  description = "Runs the ktlint formatter on all sources in this project and included builds"
  dependsOn(
    gradle.includedBuild("build-logic").task(":plugins:ktlintFormat"),
    gradle.includedBuild("build-logic").task(":tools:ktlintFormat"),
    *subprojects.mapNotNull { tasks.findByPath(":${it.name}:ktlintFormat") }.toTypedArray()
  )
}