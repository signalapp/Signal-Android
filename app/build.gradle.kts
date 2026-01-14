@file:Suppress("UnstableApiUsage")

import com.android.build.api.dsl.ManagedVirtualDevice
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import java.io.File
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.jetbrains.kotlin.android)
  alias(libs.plugins.ktlint)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlinx.serialization)
  id("androidx.navigation.safeargs")
  id("kotlin-parcelize")
  id("com.squareup.wire")
  id("translations")
  id("licenses")
}

apply(from = "static-ips.gradle.kts")

val canonicalVersionCode = 1638
val canonicalVersionName = "7.70.1"
val currentHotfixVersion = 0
val maxHotfixVersions = 100

// We don't want versions to ever end in 0 so that they don't conflict with nightly versions
val possibleHotfixVersions = (0 until maxHotfixVersions).toList().filter { it % 10 != 0 }

val debugKeystorePropertiesProvider = providers.of(PropertiesFileValueSource::class.java) {
  parameters.file.set(rootProject.layout.projectDirectory.file("keystore.debug.properties"))
}

val languagesProvider = providers.of(LanguageListValueSource::class.java) {
  parameters.resDir.set(layout.projectDirectory.dir("src/main/res"))
}

val languagesForBuildConfigProvider = languagesProvider.map { languages ->
  languages.joinToString(separator = ", ") { language -> "\"$language\"" }
}

val selectableVariants = listOf(
  "nightlyBackupRelease",
  "nightlyBackupSpinner",
  "nightlyProdSpinner",
  "nightlyProdPerf",
  "nightlyProdRelease",
  "nightlyStagingRelease",
  "playProdDebug",
  "playProdSpinner",
  "playProdCanary",
  "playProdPerf",
  "playProdBenchmark",
  "playProdInstrumentation",
  "playProdRelease",
  "playStagingDebug",
  "playStagingCanary",
  "playStagingSpinner",
  "playStagingPerf",
  "playStagingInstrumentation",
  "playStagingRelease",
  "websiteProdSpinner",
  "websiteProdRelease"
)

wire {
  kotlin {
    javaInterop = true
  }

  sourcePath {
    srcDir("src/main/protowire")
  }

  protoPath {
    srcDir("${project.rootDir}/lib/libsignal-service/src/main/protowire")
  }
  // Handled by libsignal
  prune("signalservice.DecryptionErrorMessage")
}

ktlint {
  version.set("1.5.0")
}

android {
  namespace = "org.thoughtcrime.securesms"

  buildToolsVersion = libs.versions.buildTools.get()
  compileSdkVersion = libs.versions.compileSdk.get()
  ndkVersion = libs.versions.ndk.get()

  flavorDimensions += listOf("distribution", "environment")
  testBuildType = "instrumentation"

  android.bundle.language.enableSplit = false

  kotlinOptions {
    jvmTarget = libs.versions.kotlinJvmTarget.get()
    freeCompilerArgs = listOf("-Xjvm-default=all")
    suppressWarnings = true
  }

  debugKeystorePropertiesProvider.orNull?.let { properties ->
    signingConfigs.getByName("debug").apply {
      storeFile = file("${project.rootDir}/${properties.getProperty("storeFile")}")
      storePassword = properties.getProperty("storePassword")
      keyAlias = properties.getProperty("keyAlias")
      keyPassword = properties.getProperty("keyPassword")
    }
  }

  testOptions {
    execution = "ANDROIDX_TEST_ORCHESTRATOR"

    unitTests {
      isIncludeAndroidResources = true
    }

    managedDevices {
      devices {
        create<ManagedVirtualDevice>("pixel3api30") {
          device = "Pixel 3"
          apiLevel = 30
          systemImageSource = "google-atd"
          require64Bit = false
        }
      }
    }
  }

  sourceSets {
    getByName("test") {
      java.srcDir("$projectDir/src/testShared")
    }

    getByName("androidTest") {
      java.srcDir("$projectDir/src/testShared")
    }
  }

  compileOptions {
    isCoreLibraryDesugaringEnabled = true
    sourceCompatibility = JavaVersion.toVersion(libs.versions.javaVersion.get())
    targetCompatibility = JavaVersion.toVersion(libs.versions.javaVersion.get())
  }

  packaging {
    jniLibs {
      excludes += setOf(
        "**/*.dylib",
        "**/*.dll"
      )
    }
    resources {
      excludes += setOf(
        "LICENSE.txt",
        "LICENSE",
        "NOTICE",
        "asm-license.txt",
        "META-INF/LICENSE",
        "META-INF/LICENSE.md",
        "META-INF/NOTICE",
        "META-INF/LICENSE-notice.md",
        "META-INF/proguard/androidx-annotations.pro",
        "**/*.dylib",
        "**/*.dll"
      )
    }
  }

  buildFeatures {
    buildConfig = true
    viewBinding = true
    compose = true
  }

  composeOptions {
    kotlinCompilerExtensionVersion = "1.5.4"
  }

  defaultConfig {
    if (currentHotfixVersion >= maxHotfixVersions) {
      throw AssertionError("Hotfix version offset is too large!")
    }
    versionCode = (canonicalVersionCode * maxHotfixVersions) + possibleHotfixVersions[currentHotfixVersion]
    versionName = canonicalVersionName

    minSdk = libs.versions.minSdk.get().toInt()
    targetSdk = libs.versions.targetSdk.get().toInt()

    vectorDrawables.useSupportLibrary = true
    project.ext.set("archivesBaseName", "Signal")

    manifestPlaceholders["mapsKey"] = "AIzaSyCSx9xea86GwDKGznCAULE9Y5a8b-TfN9U"

    buildConfigField("long", "BUILD_TIMESTAMP", getLastCommitTimestamp() + "L")
    buildConfigField("String", "GIT_HASH", "\"${getGitHash()}\"")
    buildConfigField("String", "SIGNAL_URL", "\"https://chat.signal.org\"")
    buildConfigField("String", "STORAGE_URL", "\"https://storage.signal.org\"")
    buildConfigField("String", "SIGNAL_CDN_URL", "\"https://cdn.signal.org\"")
    buildConfigField("String", "SIGNAL_CDN2_URL", "\"https://cdn2.signal.org\"")
    buildConfigField("String", "SIGNAL_CDN3_URL", "\"https://cdn3.signal.org\"")
    buildConfigField("String", "SIGNAL_CDSI_URL", "\"https://cdsi.signal.org\"")
    buildConfigField("String", "SIGNAL_SERVICE_STATUS_URL", "\"uptime.signal.org\"")
    buildConfigField("String", "SIGNAL_SVR2_URL", "\"https://svr2.signal.org\"")
    buildConfigField("String", "SIGNAL_SFU_URL", "\"https://sfu.voip.signal.org\"")
    buildConfigField("String", "SIGNAL_STAGING_SFU_URL", "\"https://sfu.staging.voip.signal.org\"")
    buildConfigField("String[]", "SIGNAL_SFU_INTERNAL_NAMES", "new String[]{\"Test\", \"Staging\", \"Development\"}")
    buildConfigField("String[]", "SIGNAL_SFU_INTERNAL_URLS", "new String[]{\"https://sfu.test.voip.signal.org\", \"https://sfu.staging.voip.signal.org\", \"https://sfu.staging.test.voip.signal.org\"}")
    buildConfigField("String", "CONTENT_PROXY_HOST", "\"contentproxy.signal.org\"")
    buildConfigField("int", "CONTENT_PROXY_PORT", "443")
    buildConfigField("String[]", "SIGNAL_SERVICE_IPS", rootProject.extra["service_ips"] as String)
    buildConfigField("String[]", "SIGNAL_STORAGE_IPS", rootProject.extra["storage_ips"] as String)
    buildConfigField("String[]", "SIGNAL_CDN_IPS", rootProject.extra["cdn_ips"] as String)
    buildConfigField("String[]", "SIGNAL_CDN2_IPS", rootProject.extra["cdn2_ips"] as String)
    buildConfigField("String[]", "SIGNAL_CDN3_IPS", rootProject.extra["cdn3_ips"] as String)
    buildConfigField("String[]", "SIGNAL_SFU_IPS", rootProject.extra["sfu_ips"] as String)
    buildConfigField("String[]", "SIGNAL_CONTENT_PROXY_IPS", rootProject.extra["content_proxy_ips"] as String)
    buildConfigField("String[]", "SIGNAL_CDSI_IPS", rootProject.extra["cdsi_ips"] as String)
    buildConfigField("String[]", "SIGNAL_SVR2_IPS", rootProject.extra["svr2_ips"] as String)
    buildConfigField("String", "SIGNAL_AGENT", "\"OWA\"")
    buildConfigField("String", "SVR2_MRENCLAVE_LEGACY", "\"093be9ea32405e85ae28dbb48eb668aebeb7dbe29517b9b86ad4bec4dfe0e6a6\"")
    buildConfigField("String", "SVR2_MRENCLAVE", "\"29cd63c87bea751e3bfd0fbd401279192e2e5c99948b4ee9437eafc4968355fb\"")
    buildConfigField("String[]", "UNIDENTIFIED_SENDER_TRUST_ROOTS", "new String[]{ \"BXu6QIKVz5MA8gstzfOgRQGqyLqOwNKHL6INkv3IHWMF\", \"BUkY0I+9+oPgDCn4+Ac6Iu813yvqkDr/ga8DzLxFxuk6\"}")
    buildConfigField("String", "ZKGROUP_SERVER_PUBLIC_PARAMS", "\"AMhf5ywVwITZMsff/eCyudZx9JDmkkkbV6PInzG4p8x3VqVJSFiMvnvlEKWuRob/1eaIetR31IYeAbm0NdOuHH8Qi+Rexi1wLlpzIo1gstHWBfZzy1+qHRV5A4TqPp15YzBPm0WSggW6PbSn+F4lf57VCnHF7p8SvzAA2ZZJPYJURt8X7bbg+H3i+PEjH9DXItNEqs2sNcug37xZQDLm7X36nOoGPs54XsEGzPdEV+itQNGUFEjY6X9Uv+Acuks7NpyGvCoKxGwgKgE5XyJ+nNKlyHHOLb6N1NuHyBrZrgtY/JYJHRooo5CEqYKBqdFnmbTVGEkCvJKxLnjwKWf+fEPoWeQFj5ObDjcKMZf2Jm2Ae69x+ikU5gBXsRmoF94GXTLfN0/vLt98KDPnxwAQL9j5V1jGOY8jQl6MLxEs56cwXN0dqCnImzVH3TZT1cJ8SW1BRX6qIVxEzjsSGx3yxF3suAilPMqGRp4ffyopjMD1JXiKR2RwLKzizUe5e8XyGOy9fplzhw3jVzTRyUZTRSZKkMLWcQ/gv0E4aONNqs4P+NameAZYOD12qRkxosQQP5uux6B2nRyZ7sAV54DgFyLiRcq1FvwKw2EPQdk4HDoePrO/RNUbyNddnM/mMgj4FW65xCoT1LmjrIjsv/Ggdlx46ueczhMgtBunx1/w8k8V+l8LVZ8gAT6wkU5J+DPQalQguMg12Jzug3q4TbdHiGCmD9EunCwOmsLuLJkz6EcSYXtrlDEnAM+hicw7iergYLLlMXpfTdGxJCWJmP4zqUFeTTmsmhsjGBt7NiEB/9pFFEB3pSbf4iiUukw63Eo8Aqnf4iwob6X1QviCWuc8t0LUlT9vALgh/f2DPVOOmR0RW6bgRvc7DSF20V/omg+YBw==\"")
    buildConfigField("String", "GENERIC_SERVER_PUBLIC_PARAMS", "\"AByD873dTilmOSG0TjKrvpeaKEsUmIO8Vx9BeMmftwUs9v7ikPwM8P3OHyT0+X3EUMZrSe9VUp26Wai51Q9I8mdk0hX/yo7CeFGJyzoOqn8e/i4Ygbn5HoAyXJx5eXfIbqpc0bIxzju4H/HOQeOpt6h742qii5u/cbwOhFZCsMIbElZTaeU+BWMBQiZHIGHT5IE0qCordQKZ5iPZom0HeFa8Yq0ShuEyAl0WINBiY6xE3H/9WnvzXBbMuuk//eRxXgzO8ieCeK8FwQNxbfXqZm6Ro1cMhCOF3u7xoX83QhpN\"")
    buildConfigField("String", "BACKUP_SERVER_PUBLIC_PARAMS", "\"AJwNSU55fsFCbgaxGRD11wO1juAs8Yr5GF8FPlGzzvdJJIKH5/4CC7ZJSOe3yL2vturVaRU2Cx0n751Vt8wkj1bozK3CBV1UokxV09GWf+hdVImLGjXGYLLhnI1J2TWEe7iWHyb553EEnRb5oxr9n3lUbNAJuRmFM7hrr0Al0F0wrDD4S8lo2mGaXe0MJCOM166F8oYRQqpFeEHfiLnxA1O8ZLh7vMdv4g9jI5phpRBTsJ5IjiJrWeP0zdIGHEssUeprDZ9OUJ14m0v61eYJMKsf59Bn+mAT2a7YfB+Don9O\"")
    buildConfigField("String[]", "LANGUAGES", "new String[]{ ${languagesForBuildConfigProvider.get()} }")
    buildConfigField("int", "CANONICAL_VERSION_CODE", "$canonicalVersionCode")
    buildConfigField("String", "DEFAULT_CURRENCIES", "\"EUR,AUD,GBP,CAD,CNY\"")
    buildConfigField("String", "GIPHY_API_KEY", "\"3o6ZsYH6U6Eri53TXy\"")
    buildConfigField("String", "SIGNAL_CAPTCHA_URL", "\"https://signalcaptchas.org/registration/generate.html\"")
    buildConfigField("String", "RECAPTCHA_PROOF_URL", "\"https://signalcaptchas.org/challenge/generate.html\"")
    buildConfigField("org.signal.libsignal.net.Network.Environment", "LIBSIGNAL_NET_ENV", "org.signal.libsignal.net.Network.Environment.PRODUCTION")
    buildConfigField("int", "LIBSIGNAL_LOG_LEVEL", "org.signal.libsignal.protocol.logging.SignalProtocolLogger.INFO")

    buildConfigField("String", "BUILD_DISTRIBUTION_TYPE", "\"unset\"")
    buildConfigField("String", "BUILD_ENVIRONMENT_TYPE", "\"unset\"")
    buildConfigField("String", "BUILD_VARIANT_TYPE", "\"unset\"")
    buildConfigField("String", "BADGE_STATIC_ROOT", "\"https://updates2.signal.org/static/badges/\"")
    buildConfigField("String", "STRIPE_BASE_URL", "\"https://api.stripe.com/v1\"")
    buildConfigField("String", "STRIPE_PUBLISHABLE_KEY", "\"pk_live_6cmGZopuTsV8novGgJJW9JpC00vLIgtQ1D\"")
    buildConfigField("boolean", "TRACING_ENABLED", "false")
    buildConfigField("boolean", "LINK_DEVICE_UX_ENABLED", "false")
    buildConfigField("boolean", "USE_STRING_ID", "true")

    ndk {
      abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
    }
    resourceConfigurations += listOf()

    splits {
      abi {
        isEnable = !project.hasProperty("generateBaselineProfile")
        reset()
        include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        isUniversalApk = true
      }
    }

    testInstrumentationRunner = "org.thoughtcrime.securesms.testing.SignalTestRunner"
    testInstrumentationRunnerArguments["clearPackageData"] = "true"
  }

  buildTypes {
    getByName("debug") {
      if (debugKeystorePropertiesProvider.orNull != null) {
        signingConfig = signingConfigs["debug"]
      }
      isDefault = true
      isMinifyEnabled = false
      proguardFiles(
        getDefaultProguardFile("proguard-android.txt"),
        "proguard/proguard-firebase-messaging.pro",
        "proguard/proguard-google-play-services.pro",
        "proguard/proguard-jackson.pro",
        "proguard/proguard-sqlite.pro",
        "proguard/proguard-appcompat-v7.pro",
        "proguard/proguard-square-okhttp.pro",
        "proguard/proguard-square-okio.pro",
        "proguard/proguard-rounded-image-view.pro",
        "proguard/proguard-glide.pro",
        "proguard/proguard-shortcutbadger.pro",
        "proguard/proguard-retrofit.pro",
        "proguard/proguard-klinker.pro",
        "proguard/proguard-mobilecoin.pro",
        "proguard/proguard-retrolambda.pro",
        "proguard/proguard-okhttp.pro",
        "proguard/proguard-ez-vcard.pro",
        "proguard/proguard.cfg"
      )
      testProguardFiles(
        "proguard/proguard-automation.pro",
        "proguard/proguard.cfg"
      )

      manifestPlaceholders["mapsKey"] = getMapsKey()

      buildConfigField("String", "BUILD_VARIANT_TYPE", "\"Debug\"")
      buildConfigField("boolean", "LINK_DEVICE_UX_ENABLED", "true")
    }

    getByName("release") {
      isMinifyEnabled = true
      proguardFiles(*buildTypes["debug"].proguardFiles.toTypedArray())
      buildConfigField("String", "BUILD_VARIANT_TYPE", "\"Release\"")
    }

    create("instrumentation") {
      initWith(getByName("debug"))
      isDefault = false
      isMinifyEnabled = false
      matchingFallbacks += "debug"
      applicationIdSuffix = ".instrumentation"

      buildConfigField("String", "BUILD_VARIANT_TYPE", "\"Instrumentation\"")
      buildConfigField("String", "STRIPE_BASE_URL", "\"http://127.0.0.1:8080/stripe\"")
    }

    create("spinner") {
      initWith(getByName("debug"))
      isDefault = false
      isMinifyEnabled = false
      matchingFallbacks += "debug"
      buildConfigField("String", "BUILD_VARIANT_TYPE", "\"Spinner\"")
    }

    create("perf") {
      initWith(getByName("debug"))
      isDefault = false
      isDebuggable = false
      isMinifyEnabled = true
      matchingFallbacks += "debug"
      buildConfigField("String", "BUILD_VARIANT_TYPE", "\"Perf\"")
      buildConfigField("boolean", "TRACING_ENABLED", "true")
    }

    create("benchmark") {
      initWith(getByName("debug"))
      isDefault = false
      isDebuggable = false
      isMinifyEnabled = true
      matchingFallbacks += "debug"
      buildConfigField("String", "BUILD_VARIANT_TYPE", "\"Benchmark\"")
      buildConfigField("boolean", "TRACING_ENABLED", "true")
    }

    create("canary") {
      initWith(getByName("debug"))
      isDefault = false
      isMinifyEnabled = false
      matchingFallbacks += "debug"
      buildConfigField("String", "BUILD_VARIANT_TYPE", "\"Canary\"")
    }
  }

  productFlavors {
    create("play") {
      dimension = "distribution"
      isDefault = true
      buildConfigField("boolean", "MANAGES_APP_UPDATES", "false")
      buildConfigField("String", "APK_UPDATE_MANIFEST_URL", "null")
      buildConfigField("String", "BUILD_DISTRIBUTION_TYPE", "\"play\"")
    }

    create("website") {
      dimension = "distribution"
      buildConfigField("boolean", "MANAGES_APP_UPDATES", "true")
      buildConfigField("String", "APK_UPDATE_MANIFEST_URL", "\"https://updates.signal.org/android/latest.json\"")
      buildConfigField("String", "BUILD_DISTRIBUTION_TYPE", "\"website\"")
    }

    create("nightly") {
      dimension = "distribution"
      versionNameSuffix = "-nightly-untagged-${getGitHash()}"
      buildConfigField("boolean", "MANAGES_APP_UPDATES", "false")
      buildConfigField("String", "APK_UPDATE_MANIFEST_URL", "null")
      buildConfigField("String", "BUILD_DISTRIBUTION_TYPE", "\"nightly\"")
      buildConfigField("boolean", "LINK_DEVICE_UX_ENABLED", "true")
    }

    create("prod") {
      dimension = "environment"

      isDefault = true

      buildConfigField("String", "MOBILE_COIN_ENVIRONMENT", "\"mainnet\"")
      buildConfigField("String", "BUILD_ENVIRONMENT_TYPE", "\"Prod\"")
    }

    create("staging") {
      dimension = "environment"

      applicationIdSuffix = ".staging"

      buildConfigField("String", "SIGNAL_URL", "\"https://chat.staging.signal.org\"")
      buildConfigField("String", "STORAGE_URL", "\"https://storage-staging.signal.org\"")
      buildConfigField("String", "SIGNAL_CDN_URL", "\"https://cdn-staging.signal.org\"")
      buildConfigField("String", "SIGNAL_CDN2_URL", "\"https://cdn2-staging.signal.org\"")
      buildConfigField("String", "SIGNAL_CDN3_URL", "\"https://cdn3-staging.signal.org\"")
      buildConfigField("String", "SIGNAL_CDSI_URL", "\"https://cdsi.staging.signal.org\"")
      buildConfigField("String", "SIGNAL_SVR2_URL", "\"https://svr2.staging.signal.org\"")
      buildConfigField("String", "SVR2_MRENCLAVE_LEGACY", "\"2e8cefe6e3f389d8426adb24e9b7fb7adf10902c96f06f7bbcee36277711ed91\"")
      buildConfigField("String", "SVR2_MRENCLAVE", "\"a75542d82da9f6914a1e31f8a7407053b99cc99a0e7291d8fbd394253e19b036\"")
      buildConfigField("String[]", "UNIDENTIFIED_SENDER_TRUST_ROOTS", "new String[]{\"BbqY1DzohE4NUZoVF+L18oUPrK3kILllLEJh2UnPSsEx\", \"BYhU6tPjqP46KGZEzRs1OL4U39V5dlPJ/X09ha4rErkm\"}")
      buildConfigField("String", "ZKGROUP_SERVER_PUBLIC_PARAMS", "\"ABSY21VckQcbSXVNCGRYJcfWHiAMZmpTtTELcDmxgdFbtp/bWsSxZdMKzfCp8rvIs8ocCU3B37fT3r4Mi5qAemeGeR2X+/YmOGR5ofui7tD5mDQfstAI9i+4WpMtIe8KC3wU5w3Inq3uNWVmoGtpKndsNfwJrCg0Hd9zmObhypUnSkfYn2ooMOOnBpfdanRtrvetZUayDMSC5iSRcXKpdlukrpzzsCIvEwjwQlJYVPOQPj4V0F4UXXBdHSLK05uoPBCQG8G9rYIGedYsClJXnbrgGYG3eMTG5hnx4X4ntARBgELuMWWUEEfSK0mjXg+/2lPmWcTZWR9nkqgQQP0tbzuiPm74H2wMO4u1Wafe+UwyIlIT9L7KLS19Aw8r4sPrXZSSsOZ6s7M1+rTJN0bI5CKY2PX29y5Ok3jSWufIKcgKOnWoP67d5b2du2ZVJjpjfibNIHbT/cegy/sBLoFwtHogVYUewANUAXIaMPyCLRArsKhfJ5wBtTminG/PAvuBdJ70Z/bXVPf8TVsR292zQ65xwvWTejROW6AZX6aqucUjlENAErBme1YHmOSpU6tr6doJ66dPzVAWIanmO/5mgjNEDeK7DDqQdB1xd03HT2Qs2TxY3kCK8aAb/0iM0HQiXjxZ9HIgYhbtvGEnDKW5ILSUydqH/KBhW4Pb0jZWnqN/YgbWDKeJxnDbYcUob5ZY5Lt5ZCMKuaGUvCJRrCtuugSMaqjowCGRempsDdJEt+cMaalhZ6gczklJB/IbdwENW9KeVFPoFNFzhxWUIS5ML9riVYhAtE6JE5jX0xiHNVIIPthb458cfA8daR0nYfYAUKogQArm0iBezOO+mPk5vCNWI+wwkyFCqNDXz/qxl1gAntuCJtSfq9OC3NkdhQlgYQ==\"")
      buildConfigField("String", "GENERIC_SERVER_PUBLIC_PARAMS", "\"AHILOIrFPXX9laLbalbA9+L1CXpSbM/bTJXZGZiuyK1JaI6dK5FHHWL6tWxmHKYAZTSYmElmJ5z2A5YcirjO/yfoemE03FItyaf8W1fE4p14hzb5qnrmfXUSiAIVrhaXVwIwSzH6RL/+EO8jFIjJ/YfExfJ8aBl48CKHgu1+A6kWynhttonvWWx6h7924mIzW0Czj2ROuh4LwQyZypex4GuOPW8sgIT21KNZaafgg+KbV7XM1x1tF3XA17B4uGUaDbDw2O+nR1+U5p6qHPzmJ7ggFjSN6Utu+35dS1sS0P9N\"")
      buildConfigField("String", "BACKUP_SERVER_PUBLIC_PARAMS", "\"AHYrGb9IfugAAJiPKp+mdXUx+OL9zBolPYHYQz6GI1gWjpEu5me3zVNSvmYY4zWboZHif+HG1sDHSuvwFd0QszSwuSF4X4kRP3fJREdTZ5MCR0n55zUppTwfHRW2S4sdQ0JGz7YDQIJCufYSKh0pGNEHL6hv79Agrdnr4momr3oXdnkpVBIp3HWAQ6IbXQVSG18X36GaicI1vdT0UFmTwU2KTneluC2eyL9c5ff8PcmiS+YcLzh0OKYQXB5ZfQ06d6DiINvDQLy75zcfUOniLAj0lGJiHxGczin/RXisKSR8\"")
      buildConfigField("String", "MOBILE_COIN_ENVIRONMENT", "\"testnet\"")
      buildConfigField("String", "SIGNAL_CAPTCHA_URL", "\"https://signalcaptchas.org/staging/registration/generate.html\"")
      buildConfigField("String", "RECAPTCHA_PROOF_URL", "\"https://signalcaptchas.org/staging/challenge/generate.html\"")
      buildConfigField("org.signal.libsignal.net.Network.Environment", "LIBSIGNAL_NET_ENV", "org.signal.libsignal.net.Network.Environment.STAGING")
      buildConfigField("int", "LIBSIGNAL_LOG_LEVEL", "org.signal.libsignal.protocol.logging.SignalProtocolLogger.DEBUG")
      buildConfigField("boolean", "USE_STRING_ID", "false")

      buildConfigField("String", "BUILD_ENVIRONMENT_TYPE", "\"Staging\"")
      buildConfigField("String", "STRIPE_PUBLISHABLE_KEY", "\"pk_test_sngOd8FnXNkpce9nPXawKrJD00kIDngZkD\"")
    }

    create("backup") {
      initWith(getByName("staging"))

      dimension = "environment"

      applicationIdSuffix = ".backup"

      buildConfigField("boolean", "MANAGES_APP_UPDATES", "true")
      buildConfigField("String", "BUILD_ENVIRONMENT_TYPE", "\"Backup\"")
    }
  }

  lint {
    abortOnError = true
    baseline = file("lint-baseline.xml")
    checkReleaseBuilds = false
    ignoreWarnings = true
    quiet = true
    disable += "LintError"
  }

  androidComponents {
    beforeVariants { variant ->
      variant.enable = variant.name in selectableVariants
    }
    onVariants(selector().all()) { variant: com.android.build.api.variant.ApplicationVariant ->
      // Include the test-only library on debug builds.
      if (variant.buildType != "instrumentation") {
        variant.packaging.jniLibs.excludes.add("**/libsignal_jni_testing.so")
      }

      // Starting with minSdk 23, Android leaves native libraries uncompressed, which is fine for the Play Store, but not for our self-distributed APKs.
      // This reverts it to the legacy behavior, compressing the native libraries, and drastically reducing the APK file size.
      if (variant.name.contains("website", ignoreCase = true)) {
        variant.packaging.jniLibs.useLegacyPackaging.set(true)
      }

      // Version overrides
      if (variant.name.contains("nightly", ignoreCase = true)) {
        var tag = getNightlyTagForCurrentCommit()
        if (!tag.isNullOrEmpty()) {
          if (tag.startsWith("v")) {
            tag = tag.substring(1)
          }

          // We add a multiple of maxHotfixVersions to nightlies to ensure we're always at least that many versions ahead
          val nightlyBuffer = (5 * maxHotfixVersions)
          val nightlyVersionCode = (canonicalVersionCode * maxHotfixVersions) + (getNightlyBuildNumber(tag) * 10) + nightlyBuffer

          variant.outputs.forEach { output ->
            output.versionName.set(tag)
            output.versionCode.set(nightlyVersionCode)
          }
        }
      }
    }
  }

  val releaseDir = "$projectDir/src/release/java"
  val debugDir = "$projectDir/src/debug/java"

  android.buildTypes.configureEach {
    val path = if (name == "release") releaseDir else debugDir
    sourceSets.named(name) {
      java.srcDir(path)
    }
  }
}

dependencies {
  lintChecks(project(":lintchecks"))
  ktlintRuleset(libs.ktlint.twitter.compose)
  coreLibraryDesugaring(libs.android.tools.desugar)

  implementation(project(":lib:libsignal-service"))
  implementation(project(":lib:paging"))
  implementation(project(":core:util"))
  implementation(project(":lib:glide-config"))
  implementation(project(":lib:video"))
  implementation(project(":lib:device-transfer"))
  implementation(project(":lib:image-editor"))
  implementation(project(":lib:donations"))
  implementation(project(":lib:debuglogs-viewer"))
  implementation(project(":lib:contacts"))
  implementation(project(":lib:qr"))
  implementation(project(":lib:sticky-header-grid"))
  implementation(project(":lib:photoview"))
  implementation(project(":core:ui"))
  implementation(project(":core:models"))

  implementation(libs.androidx.fragment.ktx)
  implementation(libs.androidx.appcompat) {
    version {
      strictly("1.6.1")
    }
  }
  implementation(libs.androidx.window.window)
  implementation(libs.androidx.window.java)
  implementation(libs.androidx.recyclerview)
  implementation(libs.material.material)
  implementation(libs.androidx.legacy.support)
  implementation(libs.androidx.preference)
  implementation(libs.androidx.legacy.preference)
  implementation(libs.androidx.gridlayout)
  implementation(libs.androidx.exifinterface)
  implementation(libs.androidx.compose.rxjava3)
  implementation(libs.androidx.compose.runtime.livedata)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.constraintlayout)
  implementation(libs.androidx.navigation.fragment.ktx)
  implementation(libs.androidx.navigation.ui.ktx)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.lifecycle.viewmodel.ktx)
  implementation(libs.androidx.lifecycle.livedata.ktx)
  implementation(libs.androidx.lifecycle.process)
  implementation(libs.androidx.lifecycle.viewmodel.savedstate)
  implementation(libs.androidx.lifecycle.common.java8)
  implementation(libs.androidx.lifecycle.reactivestreams.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.extensions)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)
  implementation(libs.androidx.concurrent.futures)
  implementation(libs.androidx.autofill)
  implementation(libs.androidx.biometric)
  implementation(libs.androidx.sharetarget)
  implementation(libs.androidx.profileinstaller)
  implementation(libs.androidx.asynclayoutinflater)
  implementation(libs.androidx.asynclayoutinflater.appcompat)
  implementation(libs.androidx.emoji2)
  implementation(libs.firebase.messaging) {
    exclude(group = "com.google.firebase", module = "firebase-core")
    exclude(group = "com.google.firebase", module = "firebase-analytics")
    exclude(group = "com.google.firebase", module = "firebase-measurement-connector")
  }
  implementation(libs.google.play.services.maps)
  implementation(libs.google.play.services.auth)
  implementation(libs.google.signin)
  implementation(libs.bundles.media3)
  implementation(libs.conscrypt.android)
  implementation(libs.signal.aesgcmprovider)
  implementation(libs.libsignal.android)
  implementation(libs.mobilecoin)
  implementation(libs.signal.ringrtc)
  implementation(libs.leolin.shortcutbadger)
  implementation(libs.emilsjolander.stickylistheaders)
  implementation(libs.glide.glide)
  implementation(libs.roundedimageview)
  implementation(libs.materialish.progress)
  implementation(libs.greenrobot.eventbus)
  implementation(libs.google.zxing.android.integration)
  implementation(libs.google.zxing.core)
  implementation(libs.google.flexbox)
  implementation(libs.subsampling.scale.image.view) {
    exclude(group = "com.android.support", module = "support-annotations")
  }
  implementation(libs.android.tooltips) {
    exclude(group = "com.android.support", module = "appcompat-v7")
  }
  implementation(libs.stream)
  implementation(libs.lottie)
  implementation(libs.lottie.compose)
  implementation(libs.signal.android.database.sqlcipher)
  implementation(libs.androidx.sqlite)
  testImplementation(libs.androidx.sqlite.framework)
  implementation(libs.google.ez.vcard) {
    exclude(group = "com.fasterxml.jackson.core")
    exclude(group = "org.freemarker")
  }
  implementation(libs.dnsjava)
  implementation(libs.kotlinx.collections.immutable)
  implementation(libs.accompanist.permissions)
  implementation(libs.accompanist.drawablepainter)
  implementation(libs.kotlin.stdlib.jdk8)
  implementation(libs.kotlin.reflect)
  implementation(libs.kotlinx.coroutines.play.services)
  implementation(libs.kotlinx.coroutines.rx3)
  implementation(libs.jackson.module.kotlin)
  implementation(libs.rxjava3.rxandroid)
  implementation(libs.rxjava3.rxkotlin)
  implementation(libs.rxdogtag)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.compat)
  implementation(libs.kotlinx.serialization.json)

  implementation(project(":lib:billing"))

  "spinnerImplementation"(project(":lib:spinner"))

  "canaryImplementation"(libs.square.leakcanary)

  "instrumentationImplementation"(libs.androidx.fragment.testing) {
    exclude(group = "androidx.test", module = "core")
  }

  testImplementation(testLibs.junit.junit)
  testImplementation(testLibs.assertk)
  testImplementation(testLibs.androidx.test.core)
  testImplementation(testLibs.robolectric.robolectric) {
    exclude(group = "com.google.protobuf", module = "protobuf-java")
  }
  testImplementation(testLibs.bouncycastle.bcprov.jdk15on) {
    version {
      strictly("1.70")
    }
  }
  testImplementation(testLibs.bouncycastle.bcpkix.jdk15on) {
    version {
      strictly("1.70")
    }
  }
  testImplementation(testLibs.conscrypt.openjdk.uber)
  testImplementation(testLibs.mockk)
  testImplementation(testFixtures(project(":lib:libsignal-service")))
  testImplementation(testLibs.espresso.core)
  testImplementation(testLibs.kotlinx.coroutines.test)
  testImplementation(libs.androidx.compose.ui.test.junit4)

  "perfImplementation"(libs.androidx.compose.ui.test.manifest)

  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(testLibs.androidx.test.ext.junit)
  androidTestImplementation(testLibs.espresso.core)
  androidTestImplementation(testLibs.androidx.test.core)
  androidTestImplementation(testLibs.androidx.test.core.ktx)
  androidTestImplementation(testLibs.androidx.test.ext.junit.ktx)
  androidTestImplementation(testLibs.assertk)
  androidTestImplementation(testLibs.mockk.android)
  androidTestImplementation(testLibs.diff.utils)

  androidTestUtil(testLibs.androidx.test.orchestrator)
}

tasks.withType<Test>().configureEach {
  testLogging {
    events("failed")
    exceptionFormat = TestExceptionFormat.FULL
    showCauses = true
    showExceptions = true
    showStackTraces = true
  }
}

fun getLastCommitTimestamp(): String {
  return providers.exec {
    commandLine("git", "log", "-1", "--pretty=format:%ct")
  }.standardOutput.asText.get() + "000"
}

fun getGitHash(): String {
  return providers.exec {
    commandLine("git", "rev-parse", "HEAD")
  }.standardOutput.asText.get().trim().substring(0, 12)
}

fun getNightlyTagForCurrentCommit(): String? {
  val output = providers.exec {
    commandLine("git", "tag", "--points-at", "HEAD")
  }.standardOutput.asText.get().trim()

  return if (output.isNotEmpty()) {
    val tags = output.split("\n").toList()
    tags.firstOrNull { it.contains("nightly") } ?: tags[0]
  } else {
    null
  }
}

fun getNightlyBuildNumber(tag: String?): Int {
  if (tag == null) {
    return 0
  }

  val match = Regex("-(\\d{3})$").find(tag)
  return match?.groupValues?.get(1)?.toIntOrNull() ?: 0
}

fun getMapsKey(): String {
  return providers
    .gradleProperty("mapsKey")
    .orElse(providers.environmentVariable("MAPS_KEY"))
    .orElse("AIzaSyCSx9xea86GwDKGznCAULE9Y5a8b-TfN9U")
    .get()
}

abstract class LanguageListValueSource : ValueSource<List<String>, LanguageListValueSource.Params> {
  interface Params : ValueSourceParameters {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val resDir: DirectoryProperty
  }

  override fun obtain(): List<String> {
    // In API 35, language codes for Hebrew and Indonesian now use the ISO 639-1 code ("he" and "id").
    // However, the value resources still only support the outdated code ("iw" and "in") so we have
    // to manually indicate that we support these languages.
    val updatedLanguageCodes = listOf("he", "id")

    val resRoot = parameters.resDir.asFile.get()

    val languages = resRoot
      .walkTopDown()
      .filter { it.isFile && it.name == "strings.xml" }
      .mapNotNull { stringFile -> stringFile.parentFile?.name }
      .map { valuesFolderName -> valuesFolderName.removePrefix("values-") }
      .filter { valuesFolderName -> valuesFolderName != "values" }
      .map { languageCode -> languageCode.replace("-r", "_") }
      .toList()
      .distinct()
      .sorted()

    return languages + updatedLanguageCodes + "en"
  }
}

abstract class PropertiesFileValueSource : ValueSource<Properties?, PropertiesFileValueSource.Params> {
  interface Params : ValueSourceParameters {
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val file: RegularFileProperty
  }

  override fun obtain(): Properties? {
    val f: File = parameters.file.asFile.get()
    if (!f.exists()) return null

    return Properties().apply {
      f.inputStream().use { load(it) }
    }
  }
}

fun String.capitalize(): String {
  return this.replaceFirstChar { it.uppercase() }
}
