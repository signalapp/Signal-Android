/**
 * build.gradle.kts for the new ':fips-crypto-bridge' module.
 *
 * This script orchestrates the compilation of the Rust JNI bridge and the C
 * OpenSSL bridge, linking them together and preparing them for inclusion in the
 * main Signal-Android APK. This version is tailored for the Client-Only model.
 *
 * Assumed Project Structure:
 * /Signal-Android
 * |-- app/
 * |-- fips-crypto-bridge/
 * |   |-- build.gradle.kts  (This file)
 * |   |-- Cargo.toml
 * |   |-- src/
 * |   |   |-- main/
 * |   |   |   |-- rust/ (Contains the Rust JNI bridge code)
 * |   |   |   |-- c/ (Contains the C bridge code and CMakeLists.txt)
 * |-- build.gradle.kts
 * |-- settings.gradle.kts
 *
 * Prerequisites:
 * 1. The 'com.github.williamfzc.cargo-ndk' plugin is applied in the root build script.
 * 2. The Android NDK is installed and its path is known to Gradle.
 * 3. Pre-compiled OpenSSL 3.x libraries (libcrypto.so) and the FIPS provider (fips.so)
 * are located in a known directory.
 */

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    // This plugin from https://github.com/williamfzc/cargo-ndk-android
    // simplifies compiling Rust code for Android.
    id("com.github.williamfzc.cargo-ndk") version "0.2.2"
}

android {
    namespace = "org.thoughtcrime.securesms.crypto.fips"
    compileSdk = 34 // Match Signal's target SDK

    defaultConfig {
        minSdk = 23 // Match Signal's min SDK
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        // --- CMake Configuration ---
        // Configures the build process for the C part of our bridge.
        externalNativeBuild {
            cmake {
                // Arguments passed to CMake. We pass the location of our pre-compiled
                // OpenSSL libraries so CMake can find and link against them.
                arguments(
                    "-DOPENSSL_LIBS_DIR=${project.projectDir}/libs/openssl"
                )
                cppFlags("-std=c++17")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {}
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    // --- Native Build Configuration ---
    // Tells Gradle where to find our native source code and how to build it.
    externalNativeBuild {
        cmake {
            path = file("src/main/c/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // Ensures that the output of our native builds is correctly packaged.
    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("libs")
        }
    }
}

// --- Rust (Cargo-NDK) Configuration ---
cargoNdk {
    ndkVersion = "25.1.8937393"
    apiLevel = 23
    // The name of our Rust library, as defined in Cargo.toml.
    // This will produce `libfips_signal_bridge.so`.
    module = "fips_signal_bridge"
    targets = listOf("arm64", "arm", "x86", "x86_64")
    
    // --- Linking Configuration ---
    // This is the most critical part for connecting Rust and C.
    // We are telling the Rust compiler's linker (LD) to link against
    // the C library we are building with CMake.
    ldLibs = listOf(
        // The name of our C bridge library, as defined in its CMakeLists.txt.
        "openssl_fips_bridge",
        
        // We also need to link against the core OpenSSL crypto library.
        "crypto",
        
        // Standard libraries required on Android.
        "log", "dl", "m"
    )
    
    // We need to tell the Rust linker where to find the libraries we just specified.
    // The path points to the output directory of our CMake build for each architecture.
    ldLibsPath = mapOf(
        "aarch64-linux-android" to "build/intermediates/cmake/debug/obj/arm64-v8a",
        "armv7-linux-androideabi" to "build/intermediates/cmake/debug/obj/armeabi-v7a",
        "i686-linux-android" to "build/intermediates/cmake/debug/obj/x86",
        "x86_64-linux-android" to "build/intermediates/cmake/debug/obj/x86_64"
    )
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

// --- Task Dependencies ---
// This ensures that our C library is built *before* our Rust library attempts to link against it.
afterEvaluate {
    tasks.withType<com.github.williamfzc.simhand.SimhandTask> {
        val cmakeTasks = tasks.filter { it.name.startsWith("externalNativeBuild") }
        cmakeTasks.forEach { dependsOn(it) }
    }
}
