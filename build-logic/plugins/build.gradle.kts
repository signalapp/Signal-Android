plugins {
  `kotlin-dsl`
  alias(libs.plugins.ktlint)
  id("groovy-gradle-plugin")
}

java {
  sourceCompatibility = JavaVersion.toVersion(libs.versions.javaVersion.get())
  targetCompatibility = JavaVersion.toVersion(libs.versions.javaVersion.get())
}

kotlin {
  jvmToolchain {
    languageVersion.set(JavaLanguageVersion.of(libs.versions.kotlinJvmTarget.get()))
  }
  compilerOptions {
    suppressWarnings = true
  }
}

dependencies {
  implementation(libs.kotlin.gradle.plugin)
  implementation(libs.android.library)
  implementation(libs.android.application)
  implementation(libs.ktlint)
  implementation(project(":tools"))

  // These allow us to reference the dependency catalog inside of our compiled plugins
  implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
  implementation(files(testLibs.javaClass.superclass.protectionDomain.codeSource.location))
}

ktlint {
  filter {
    exclude { element ->
      element.file.path.contains("/build/generated-sources")
    }
  }
}

// The IDE's KotlinDslScriptsModel resolves the Groovy modules bundled with this Gradle distribution (via the
// localGroovy() dependency that groovy-gradle-plugin adds) during sync. That resolution happens in the Tooling
// API model-building phase, which no task graph -- and therefore no updateVerificationMetadata pass -- ever
// reaches, so their checksums never get written and a fresh sync fails verification. This task resolves that
// same module graph from a task so --write-verification-metadata can capture it.
run {
  val syncGroovyTaskName = "syncGroovyVerification"

  val syncTask = tasks.register(syncGroovyTaskName) {
    group = "Verification"
    description = "Resolves the Groovy modules bundled with this Gradle distribution so their checksums can be written to verification-metadata.xml. The IDE's KotlinDslScriptsModel resolves these (via localGroovy) during sync, but no task graph does, so the cross-platform/qa passes never capture them."
  }

  // Only wire up the resolvable configuration when the task is actually requested. This build is included by the
  // root, so the requested task names live on the parent build's start parameters, not this one's.
  val requested = (gradle.parent ?: gradle).startParameter.taskNames.any { it.substringAfterLast(':') == syncGroovyTaskName }
  if (requested) {
    val groovyVersion = groovy.lang.GroovySystem.getVersion()
    val suffix = "-$groovyVersion.jar"
    // localGroovy() puts the whole bundled Groovy runtime on the classpath, not just the umbrella module. Derive the
    // module names from the jars it actually resolves to (rather than every groovy jar in the distribution) so the
    // set matches what the IDE resolves and adapts automatically across Gradle versions.
    val modules = configurations.detachedConfiguration(dependencies.localGroovy()).files
      .map { it.name }
      .filter { it.startsWith("groovy") && it.endsWith(suffix) }
      .map { it.removeSuffix(suffix) }
      .sorted()
      .ifEmpty { listOf("groovy") }

    val configuration = configurations.create("groovyVerification") {
      isCanBeConsumed = false
      isCanBeResolved = true
    }
    modules.forEach { module ->
      dependencies.add(configuration.name, "org.apache.groovy:$module:$groovyVersion")
    }

    syncTask.configure {
      inputs.files(configuration.incoming.files)
      doLast {
        println("Resolved Groovy $groovyVersion module graph for verification.")
      }
    }
  }
}
