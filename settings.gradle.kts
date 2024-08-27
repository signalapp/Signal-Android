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
      url = uri("https://dl.cloudsmith.io/qxAgwaeEE1vN8aLU/mobilecoin/mobilecoin/maven/")
    }
    jcenter {
      content {
        includeVersion("mobi.upod", "time-duration-picker", "1.1.3")
      }
    }
  }
}

// To build libsignal from source, set the libsignalClientPath property in gradle.properties.
val libsignalClientPath = if (extra.has("libsignalClientPath")) extra.get("libsignalClientPath") else null;
if (libsignalClientPath is String) {
  includeBuild(rootDir.resolve(libsignalClientPath + "/java")) {
    name = "libsignal-client"
    dependencySubstitution {
      substitute(module("org.signal:libsignal-client")).using(project(":client"))
      substitute(module("org.signal:libsignal-android")).using(project(":android"))
    }
  }
}

include(":app")
include(":libsignal-service")
include(":lintchecks")
include(":paging")
include(":paging-app")
include(":core-util")
include(":core-util-jvm")
include(":glide-config")
include(":device-transfer")
include(":device-transfer-app")
include(":image-editor")
include(":image-editor-app")
include(":donations")
include(":donations-app")
include(":spinner")
include(":spinner-app")
include(":contacts")
include(":contacts-app")
include(":qr")
include(":qr-app")
include(":sticky-header-grid")
include(":photoview")
include(":core-ui")
include(":benchmark")
include(":microbenchmark")
include(":video")
include(":video-app")
include(":billing")

project(":app").name = "Signal-Android"
project(":paging").projectDir = file("paging/lib")
project(":paging-app").projectDir = file("paging/app")

project(":device-transfer").projectDir = file("device-transfer/lib")
project(":device-transfer-app").projectDir = file("device-transfer/app")

project(":image-editor").projectDir = file("image-editor/lib")
project(":image-editor-app").projectDir = file("image-editor/app")

project(":donations").projectDir = file("donations/lib")
project(":donations-app").projectDir = file("donations/app")

project(":spinner").projectDir = file("spinner/lib")
project(":spinner-app").projectDir = file("spinner/app")

project(":contacts").projectDir = file("contacts/lib")
project(":contacts-app").projectDir = file("contacts/app")

project(":qr").projectDir = file("qr/lib")
project(":qr-app").projectDir = file("qr/app")

project(":video").projectDir = file("video/lib")
project(":video-app").projectDir = file("video/app")

rootProject.name = "Signal"

apply(from = "dependencies.gradle.kts")
