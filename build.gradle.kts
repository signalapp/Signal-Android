import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.io.FileNotFoundException

plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.jetbrains.kotlin.android) apply false
  alias(libs.plugins.jetbrains.kotlin.jvm) apply false
  alias(libs.plugins.compose.compiler) apply false
  alias(libs.plugins.ktlint)
  alias(benchmarkLibs.plugins.baselineprofile) apply false
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
    classpath("com.squareup.wire:wire-gradle-plugin:6.4.0") {
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

subprojects {
  if (JavaVersion.current().isJava8Compatible) {
    allprojects {
      tasks.withType<Javadoc> {
        (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
      }
    }
  }

  tasks.withType<Test>().configureEach {
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 4).coerceAtLeast(1)
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
  description = "Quality Assurance. Run before release."
  dependsOn("clean")
}

tasks.register("ci") {
  group = "Verification"
  description = "Faster version of qa that's intended to be run on PRs. Uses a :fast-lint instead of full lint."
  dependsOn("clean")
}

// Wire up QA dependencies after all projects are evaluated
gradle.projectsEvaluated {
  val appTestTask = tasks.findByPath(":Signal-Android:testPlayProdDebugUnitTest")!!
  val appLintTask = tasks.findByPath(":Signal-Android:lintPlayProdDebug")!!
  val appCompileInstrumentationTask = tasks.findByPath(":Signal-Android:compilePlayProdDebugAndroidTestSources")

  tasks.named("qa") {
    dependsOn("ktlintCheck")
    dependsOn("buildQa")
    dependsOn("checkStopship")

    // Main app tasks
    dependsOn(appTestTask)
    dependsOn(appLintTask)

    // Instrumentation
    appCompileInstrumentationTask?.let { dependsOn(it) }

    // All subproject ktlint checks
    subprojects.forEach { subproject ->
      subproject.tasks.findByName("ktlintCheck")?.let { dependsOn(it) }
    }

    // Library module tasks
    subprojects.filter { it.name != "Signal-Android" }.forEach { subproject ->
      val testTask = subproject.tasks.findByName("testDebugUnitTest") ?: subproject.tasks.findByName("test")
      testTask?.let { dependsOn(it) }

      subproject.tasks.findByName("lintDebug")?.let { dependsOn(it) }
      subproject.tasks.findByName("validateDebugScreenshotTest")?.let { dependsOn(it) }
    }
  }

  tasks.named("ci") {
    dependsOn("ktlintCheck")
    dependsOn("buildQa")
    dependsOn("checkStopship")

    dependsOn(appTestTask)
    appCompileInstrumentationTask?.let { dependsOn(it) }

    dependsOn(":fast-lint:fastLint")

    subprojects.forEach { subproject ->
      subproject.tasks.findByName("ktlintCheck")?.let { dependsOn(it) }
    }

    subprojects.filter { it.name != "Signal-Android" }.forEach { subproject ->
      val testTask = subproject.tasks.findByName("testDebugUnitTest") ?: subproject.tasks.findByName("test")
      testTask?.let { dependsOn(it) }
    }
  }

  // Ensure clean runs before everything else
  rootProject.allprojects.forEach { project ->
    project.tasks.matching { it.name != "clean" }.configureEach {
      mustRunAfter("clean")
    }
  }

  // If you let all of these things run in parallel, gradle will likely OOM.
  // To avoid this, we put non-app tests and lints behind the much heavier app tests and lints.
  subprojects.filter { it.name != "Signal-Android" }.forEach { subproject ->
    appTestTask.let { task ->
      subproject.tasks.findByName("testDebugUnitTest")?.mustRunAfter(task)
      subproject.tasks.findByName("test")?.mustRunAfter(task)
    }
    appLintTask.let { task ->
      subproject.tasks.findByName("lintDebug")?.mustRunAfter(task)
    }
  }
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
    *subprojects.mapNotNull { tasks.findByPath(":${it.path}:ktlintFormat") }.toTypedArray()
  )
}

tasks.register("checkStopship") {
  val cachedProjectDir = projectDir
  doLast {
    val excludedFiles = listOf(
      "build.gradle.kts",
      "lint.xml"
    )

    val excludedDirectories = listOf(
      ".idea"
    )

    val allowedExtensions = setOf("kt", "kts", "java", "xml")

    val allFiles = cachedProjectDir.walkTopDown()
      .onEnter { it.name != "build" || it.relativeTo(cachedProjectDir).path.contains("src") }
      .asSequence()
      .filter { it.isFile && it.extension in allowedExtensions }
      .filterNot {
        val path = it.relativeTo(cachedProjectDir).path
        excludedFiles.contains(path) || excludedDirectories.any { d -> path.startsWith(d) }
      }
      .toList()

    println("[STOPSHIP Check] There are ${allFiles.size} relevant files.")

    val scope = CoroutineScope(Dispatchers.IO)
    val stopshipFiles = mutableSetOf<String>()

    runBlocking {
      allFiles.map { file ->
        scope.async {
          try {
            if (file.readText().contains("STOPSHIP")) {
              stopshipFiles += file.relativeTo(cachedProjectDir).path
            }
          } catch (e: FileNotFoundException) {
            // Ignore
          }
        }
      }
        .awaitAll()
    }

    if (stopshipFiles.isNotEmpty()) {
      throw GradleException("STOPSHIP found! Files: $stopshipFiles")
    }
  }
}
