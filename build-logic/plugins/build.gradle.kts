import org.gradle.kotlin.dsl.extra

plugins {
  `kotlin-dsl`
  id("groovy-gradle-plugin")
  id("org.jlleitschuh.gradle.ktlint") version "12.1.1"
}

val signalJavaVersion: JavaVersion by rootProject.extra
val signalKotlinJvmTarget: String by rootProject.extra

java {
  sourceCompatibility = signalJavaVersion
  targetCompatibility = signalJavaVersion
}

kotlinDslPluginOptions {
  jvmTarget.set(signalKotlinJvmTarget)
}

dependencies {
  implementation(libs.kotlin.gradle.plugin)
  implementation(libs.android.library)
  implementation(libs.android.application)
  implementation(project(":tools"))
  implementation(libs.ktlint)

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
