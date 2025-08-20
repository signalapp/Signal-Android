import org.gradle.kotlin.dsl.extra

plugins {
  `kotlin-dsl`
  alias(libs.plugins.ktlint)
  id("groovy-gradle-plugin")
}

val signalJavaVersion: JavaVersion by rootProject.extra
val signalKotlinJvmTarget: String by rootProject.extra

java {
  sourceCompatibility = signalJavaVersion
  targetCompatibility = signalJavaVersion
}

kotlin {
  jvmToolchain {
    languageVersion.set(JavaLanguageVersion.of(signalKotlinJvmTarget))
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
