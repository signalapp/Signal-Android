@file:Suppress("UnstableApiUsage")

import com.android.build.api.dsl.ManagedVirtualDevice
import org.gradle.api.JavaVersion
import org.gradle.kotlin.dsl.extra

val benchmarkLibs = the<org.gradle.accessors.dm.LibrariesForBenchmarkLibs>()

val signalBuildToolsVersion: String by rootProject.extra
val signalCompileSdkVersion: String by rootProject.extra
val signalTargetSdkVersion: Int by rootProject.extra
val signalMinSdkVersion: Int by rootProject.extra
val signalJavaVersion: JavaVersion by rootProject.extra
val signalKotlinJvmTarget: String by rootProject.extra

plugins {
    id("com.android.test")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "org.signal.benchmark"
    compileSdkVersion = signalCompileSdkVersion

    compileOptions {
        sourceCompatibility = signalJavaVersion
        targetCompatibility = signalJavaVersion
    }

    kotlinOptions {
        jvmTarget = signalKotlinJvmTarget
    }

    defaultConfig {
        minSdk = 23
        targetSdk = signalTargetSdkVersion

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        missingDimensionStrategy("environment", "prod")
        missingDimensionStrategy("distribution", "play")
    }

    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = getByName("debug").signingConfig
            matchingFallbacks += listOf("perf", "debug")
        }
    }

    targetProjectPath = ":Signal-Android"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    testOptions {
        managedDevices {
            devices {
                create("api31", ManagedVirtualDevice::class) {
                    device = "Pixel 6"
                    apiLevel = 31
                    systemImageSource = "aosp"
                    require64Bit = false
                }
            }
        }
    }

}

dependencies {
    implementation(benchmarkLibs.androidx.test.ext.junit)
    implementation(benchmarkLibs.espresso.core)
    implementation(benchmarkLibs.uiautomator)
    implementation(benchmarkLibs.androidx.benchmark.macro)
}

androidComponents {
    beforeVariants(selector().all()) {
        if (it.buildType != "benchmark") {
            it.enable = false
        }
    }
}
