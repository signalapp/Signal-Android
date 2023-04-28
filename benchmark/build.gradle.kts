@file:Suppress("UnstableApiUsage")

import com.android.build.api.dsl.ManagedVirtualDevice
import org.gradle.api.JavaVersion
import org.gradle.kotlin.dsl.extra

val benchmarkLibs = the<org.gradle.accessors.dm.LibrariesForBenchmarkLibs>()

val signalBuildToolsVersion: String by extra
val signalCompileSdkVersion: String by extra
val signalTargetSdkVersion: Int by extra
val signalMinSdkVersion: Int by extra
val signalJavaVersion: JavaVersion by extra

plugins {
    id("com.android.test")
    id("android-constants")
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
        jvmTarget = "11"
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
        it.enabled = it.buildType == "benchmark"
    }
}
