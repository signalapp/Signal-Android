pluginManagement {
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }
  includeBuild("build-logic")
}
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    mavenLocal()
    maven {
      url = uri("https://raw.githubusercontent.com/signalapp/maven/master/sqlcipher/release/")
      content {
        includeGroupByRegex("org\\.signal.*")
      }
    }
    maven {
      url = uri("https://raw.githubusercontent.com/signalapp/maven/master/aesgcmprovider/release/")
      content {
        includeGroupByRegex("org\\.signal.*")
      }
    }
    maven {
      url = uri("https://dl.cloudsmith.io/qxAgwaeEE1vN8aLU/mobilecoin/mobilecoin/maven/")
    }
    maven {
      name = "SignalBuildArtifacts"
      url = uri("https://build-artifacts.signal.org/libraries/maven/")
      content {
        includeGroupByRegex("org\\.signal.*")
      }
    }
  }
  versionCatalogs {
    // libs.versions.toml is automatically registered.
    create("benchmarkLibs") {
      from(files("gradle/benchmark-libs.versions.toml"))
    }
    create("testLibs") {
      from(files("gradle/test-libs.versions.toml"))
    }
    create("lintLibs") {
      from(files("gradle/lint-libs.versions.toml"))
    }
  }
}

// To build libsignal from source, set the libsignalClientPath property in gradle.properties.
val libsignalClientPath = if (extra.has("libsignalClientPath")) extra.get("libsignalClientPath") else null
if (libsignalClientPath is String) {
  includeBuild(rootDir.resolve(libsignalClientPath + "/java")) {
    name = "libsignal-client"
    dependencySubstitution {
      substitute(module("org.signal:libsignal-client")).using(project(":client"))
      substitute(module("org.signal:libsignal-android")).using(project(":android"))
    }
  }
}

// Main app
include(":app")

// Core modules
include(":core:util")
include(":core:util-jvm")
include(":core:models")
include(":core:ui")

// Lib modules
include(":lib:libsignal-service")
include(":lib:glide-config")
include(":lib:photoview")
include(":lib:sticky-header-grid")
include(":lib:billing")
include(":lib:paging")
include(":lib:device-transfer")
include(":lib:donations")
include(":lib:contacts")
include(":lib:qr")
include(":lib:spinner")
include(":lib:video")
include(":lib:image-editor")
include(":lib:debuglogs-viewer")

// Feature modules
include(":feature:registration")

// Demo apps
include(":demo:paging")
include(":demo:device-transfer")
include(":demo:donations")
include(":demo:contacts")
include(":demo:qr")
include(":demo:spinner")
include(":demo:video")
include(":demo:image-editor")
include(":demo:debuglogs-viewer")
include(":demo:registration")

// Testing/Lint modules
include(":lintchecks")
include(":benchmark")
include(":microbenchmark")

// App project name
project(":app").name = "Signal-Android"

rootProject.name = "Signal"
