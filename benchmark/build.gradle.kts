@file:Suppress("UnstableApiUsage")

import com.android.build.api.dsl.ManagedVirtualDevice
import org.gradle.api.JavaVersion
import org.gradle.kotlin.dsl.extra

val benchmarkLibs = the<org.gradle.accessors.dm.LibrariesForBenchmarkLibs>()

plugins {
    id("com.android.test")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "org.signal.benchmark"
    compileSdkVersion = libs.versions.compileSdk.get()

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.javaVersion.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.javaVersion.get())
    }

    kotlinOptions {
        jvmTarget = libs.versions.kotlinJvmTarget.get()
    }

    defaultConfig {
        minSdk = 23
        targetSdk = libs.versions.targetSdk.get().toInt()

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
            localDevices {
                create("api31") {
                    device = "Pixel 6"
                    testedAbi = "x86_64"
                    apiLevel = 31
                    systemImageSource = "aosp"
                    require64Bit = true
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
