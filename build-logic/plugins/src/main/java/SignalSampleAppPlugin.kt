/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.accessors.dm.LibrariesForTestLibs
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

@Suppress("UnstableApiUsage")
internal class SignalSampleAppPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    target.pluginManager.apply("com.android.application")
    target.pluginManager.apply(KtlintPlugin::class.java)
    target.pluginManager.apply(SignalBuildTaskConventionsPlugin::class.java)

    val libs = target.extensions.getByType<LibrariesForLibs>()
    val testLibs = target.extensions.getByType<LibrariesForTestLibs>()

    target.extensions.configure<ApplicationExtension> {
      buildToolsVersion = libs.versions.buildTools.get()
      compileSdkVersion(libs.versions.compileSdk.get())

      defaultConfig {
        versionCode = 1
        versionName = "1.0"

        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
      }

      compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.toVersion(libs.versions.javaVersion.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.javaVersion.get())
      }

      buildFeatures {
        buildConfig = true
        compose = true
      }
    }

    target.extensions.configure<KotlinAndroidProjectExtension> {
      compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(libs.versions.kotlinJvmTarget.get()))
        suppressWarnings.set(true)
      }
    }

    target.dependencies {
      add("coreLibraryDesugaring", libs.android.tools.desugar)

      add("implementation", target.project(":core:util"))

      add("coreLibraryDesugaring", libs.android.tools.desugar)

      add("implementation", libs.androidx.core.ktx)
      add("implementation", libs.androidx.fragment.ktx)
      add("implementation", libs.androidx.annotation)
      add("implementation", libs.androidx.appcompat)
      add("implementation", libs.rxjava3.rxandroid)
      add("implementation", libs.rxjava3.rxjava)
      add("implementation", libs.rxjava3.rxkotlin)
      add("implementation", libs.material.material)
      add("implementation", libs.androidx.constraintlayout)
      add("implementation", libs.kotlin.stdlib.jdk8)
      add("implementation", libs.androidx.activity.compose)
      add("implementation", target.dependencies.platform(libs.androidx.compose.bom))
      add("implementation", libs.androidx.compose.material3)

      add("ktlintRuleset", libs.ktlint.twitter.compose)

      add("testImplementation", testLibs.junit.junit)
      add("testImplementation", testLibs.robolectric.robolectric)
      add("testImplementation", testLibs.androidx.test.core)
      add("testImplementation", testLibs.androidx.test.core.ktx)
    }
  }
}
