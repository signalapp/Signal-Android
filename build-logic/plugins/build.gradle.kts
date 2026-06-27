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

gradlePlugin {
  plugins {
    create("dependencyVerification") {
      id = "dependency-verification"
      implementationClass = "DependencyVerificationPlugin"
    }
    create("ktlint") {
      id = "ktlint"
      implementationClass = "KtlintPlugin"
    }
    create("licenses") {
      id = "licenses"
      implementationClass = "LicensesPlugin"
    }
    create("signalBuildTaskConventions") {
      id = "signal-build-task-conventions"
      implementationClass = "SignalBuildTaskConventionsPlugin"
    }
    create("signalLibrary") {
      id = "signal-library"
      implementationClass = "SignalLibraryPlugin"
    }
    create("signalSampleApp") {
      id = "signal-sample-app"
      implementationClass = "SignalSampleAppPlugin"
    }
    create("translations") {
      id = "translations"
      implementationClass = "TranslationsPlugin"
    }
  }
}

ktlint {
  filter {
    exclude { element ->
      element.file.path.contains("/build/generated-sources")
    }
  }
}
