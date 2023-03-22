@file:Suppress("UnstableApiUsage")

import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.accessors.dm.LibrariesForTestLibs
import org.gradle.api.JavaVersion
import org.gradle.kotlin.dsl.extra

val libs = the<LibrariesForLibs>()
val testLibs = the<LibrariesForTestLibs>()

val signalBuildToolsVersion: String by extra
val signalCompileSdkVersion: String by extra
val signalTargetSdkVersion: Int by extra
val signalMinSdkVersion: Int by extra
val signalJavaVersion: JavaVersion by extra

plugins {
    id("com.android.library")
    id("kotlin-android")
    id("org.jlleitschuh.gradle.ktlint")
    id("android-constants")
}

android {
    buildToolsVersion = signalBuildToolsVersion
    compileSdkVersion = signalCompileSdkVersion

    defaultConfig {
        minSdk = signalMinSdkVersion
        targetSdk = signalTargetSdkVersion
        multiDexEnabled = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = signalJavaVersion
        targetCompatibility = signalJavaVersion
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    lint {
        disable += "InvalidVectorPath"
    }
}

ktlint {
    // Use a newer version to resolve https://github.com/JLLeitschuh/ktlint-gradle/issues/507
    version.set("0.47.1")
}

dependencies {
    lintChecks(project(":lintchecks"))

    coreLibraryDesugaring(libs.android.tools.desugar)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.appcompat)
    implementation(libs.rxjava3.rxandroid)
    implementation(libs.rxjava3.rxjava)
    implementation(libs.rxjava3.rxkotlin)
    implementation(libs.androidx.multidex)
    implementation(libs.kotlin.stdlib.jdk8)

    ktlintRuleset(libs.ktlint.twitter.compose)

    testImplementation(testLibs.junit.junit)
    testImplementation(testLibs.mockito.core)
    testImplementation(testLibs.mockito.android)
    testImplementation(testLibs.mockito.kotlin)
    testImplementation(testLibs.robolectric.robolectric)
    testImplementation(testLibs.androidx.test.core)
    testImplementation(testLibs.androidx.test.core.ktx)
}
