import com.android.build.api.dsl.ManagedVirtualDevice
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties

plugins {
  id("com.android.application")
  id("kotlin-android")
  id("androidx.navigation.safeargs")
  id("org.jlleitschuh.gradle.ktlint")
  id("org.jetbrains.kotlin.android")
  id("app.cash.exhaustive")
  id("kotlin-parcelize")
  id("com.squareup.wire")
  id("translations")
  id("licenses")
}

apply(from = "static-ips.gradle.kts")

val canonicalVersionCode = 1394
val canonicalVersionName = "7.0.0"

val postFixSize = 100
val abiPostFix: Map<String, Int> = mapOf(
  "universal" to 0,
  "armeabi-v7a" to 1,
  "arm64-v8a" to 2,
  "x86" to 3,
  "x86_64" to 4
)

val keystores: Map<String, Properties?> = mapOf("debug" to loadKeystoreProperties("keystore.debug.properties"))

val selectableVariants = listOf(
  "nightlyProdSpinner",
  "nightlyProdPerf",
  "nightlyProdRelease",
  "nightlyStagingRelease",
  "nightlyPnpPerf",
  "nightlyPnpRelease",
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
  "playPnpDebug",
  "playPnpSpinner",
  "playStagingRelease",
  "websiteProdSpinner",
  "websiteProdRelease"
)

val signalBuildToolsVersion: String by rootProject.extra
val signalCompileSdkVersion: String by rootProject.extra
val signalTargetSdkVersion: Int by rootProject.extra
val signalMinSdkVersion: Int by rootProject.extra
val signalJavaVersion: JavaVersion by rootProject.extra
val signalKotlinJvmTarget: String by rootProject.extra

wire {
  kotlin {
    javaInterop = true
  }

  sourcePath {
    srcDir("src/main/protowire")
  }

  protoPath {
    srcDir("${project.rootDir}/libsignal-service/src/main/protowire")
  }
}

ktlint {
  version.set("0.49.1")
}

android {
  namespace = "org.thoughtcrime.securesms"

  buildToolsVersion = signalBuildToolsVersion
  compileSdkVersion = signalCompileSdkVersion

  flavorDimensions += listOf("distribution", "environment")
  useLibrary("org.apache.http.legacy")
  testBuildType = "instrumentation"

  kotlinOptions {
    jvmTarget = signalKotlinJvmTarget
  }

  keystores["debug"]?.let { properties ->
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
    sourceCompatibility = signalJavaVersion
    targetCompatibility = signalJavaVersion
  }

  packagingOptions {
    resources {
      excludes += setOf("LICENSE.txt", "LICENSE", "NOTICE", "asm-license.txt", "META-INF/LICENSE", "META-INF/LICENSE.md", "META-INF/NOTICE", "META-INF/LICENSE-notice.md", "META-INF/proguard/androidx-annotations.pro", "libsignal_jni.dylib", "signal_jni.dll")
    }
  }

  buildFeatures {
    viewBinding = true
    compose = true
  }

  composeOptions {
    kotlinCompilerExtensionVersion = "1.4.4"
  }

  defaultConfig {
    versionCode = canonicalVersionCode * postFixSize
    versionName = canonicalVersionName

    minSdk = signalMinSdkVersion
    targetSdk = signalTargetSdkVersion

    multiDexEnabled = true

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
    buildConfigField("String", "SIGNAL_KEY_BACKUP_URL", "\"https://api.backup.signal.org\"")
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
    buildConfigField("String", "CDSI_MRENCLAVE", "\"0f6fd79cdfdaa5b2e6337f534d3baf999318b0c462a7ac1f41297a3e4b424a57\"")
    buildConfigField("String", "SVR2_MRENCLAVE_DEPRECATED", "\"6ee1042f9e20f880326686dd4ba50c25359f01e9f733eeba4382bca001d45094\"")
    buildConfigField("String", "SVR2_MRENCLAVE", "\"a6622ad4656e1abcd0bc0ff17c229477747d2ded0495c4ebee7ed35c1789fa97\"")
    buildConfigField("String", "UNIDENTIFIED_SENDER_TRUST_ROOT", "\"BXu6QIKVz5MA8gstzfOgRQGqyLqOwNKHL6INkv3IHWMF\"")
    buildConfigField("String", "ZKGROUP_SERVER_PUBLIC_PARAMS", "\"AMhf5ywVwITZMsff/eCyudZx9JDmkkkbV6PInzG4p8x3VqVJSFiMvnvlEKWuRob/1eaIetR31IYeAbm0NdOuHH8Qi+Rexi1wLlpzIo1gstHWBfZzy1+qHRV5A4TqPp15YzBPm0WSggW6PbSn+F4lf57VCnHF7p8SvzAA2ZZJPYJURt8X7bbg+H3i+PEjH9DXItNEqs2sNcug37xZQDLm7X36nOoGPs54XsEGzPdEV+itQNGUFEjY6X9Uv+Acuks7NpyGvCoKxGwgKgE5XyJ+nNKlyHHOLb6N1NuHyBrZrgtY/JYJHRooo5CEqYKBqdFnmbTVGEkCvJKxLnjwKWf+fEPoWeQFj5ObDjcKMZf2Jm2Ae69x+ikU5gBXsRmoF94GXTLfN0/vLt98KDPnxwAQL9j5V1jGOY8jQl6MLxEs56cwXN0dqCnImzVH3TZT1cJ8SW1BRX6qIVxEzjsSGx3yxF3suAilPMqGRp4ffyopjMD1JXiKR2RwLKzizUe5e8XyGOy9fplzhw3jVzTRyUZTRSZKkMLWcQ/gv0E4aONNqs4P+NameAZYOD12qRkxosQQP5uux6B2nRyZ7sAV54DgFyLiRcq1FvwKw2EPQdk4HDoePrO/RNUbyNddnM/mMgj4FW65xCoT1LmjrIjsv/Ggdlx46ueczhMgtBunx1/w8k8V+l8LVZ8gAT6wkU5J+DPQalQguMg12Jzug3q4TbdHiGCmD9EunCwOmsLuLJkz6EcSYXtrlDEnAM+hicw7iergYLLlMXpfTdGxJCWJmP4zqUFeTTmsmhsjGBt7NiEB/9pFFEB3pSbf4iiUukw63Eo8Aqnf4iwob6X1QviCWuc8t0I=\"")
    buildConfigField("String", "GENERIC_SERVER_PUBLIC_PARAMS", "\"AByD873dTilmOSG0TjKrvpeaKEsUmIO8Vx9BeMmftwUs9v7ikPwM8P3OHyT0+X3EUMZrSe9VUp26Wai51Q9I8mdk0hX/yo7CeFGJyzoOqn8e/i4Ygbn5HoAyXJx5eXfIbqpc0bIxzju4H/HOQeOpt6h742qii5u/cbwOhFZCsMIbElZTaeU+BWMBQiZHIGHT5IE0qCordQKZ5iPZom0HeFa8Yq0ShuEyAl0WINBiY6xE3H/9WnvzXBbMuuk//eRxXgzO8ieCeK8FwQNxbfXqZm6Ro1cMhCOF3u7xoX83QhpN\"")
    buildConfigField("String", "BACKUP_SERVER_PUBLIC_PARAMS", "\"AJwNSU55fsFCbgaxGRD11wO1juAs8Yr5GF8FPlGzzvdJJIKH5/4CC7ZJSOe3yL2vturVaRU2Cx0n751Vt8wkj1bozK3CBV1UokxV09GWf+hdVImLGjXGYLLhnI1J2TWEe7iWHyb553EEnRb5oxr9n3lUbNAJuRmFM7hrr0Al0F0wrDD4S8lo2mGaXe0MJCOM166F8oYRQqpFeEHfiLnxA1O8ZLh7vMdv4g9jI5phpRBTsJ5IjiJrWeP0zdIGHEssUeprDZ9OUJ14m0v61eYJMKsf59Bn+mAT2a7YfB+Don9O\"")
    buildConfigField("String[]", "LANGUAGES", "new String[]{ ${languageList().map { "\"$it\"" }.joinToString(separator = ", ")} }")
    buildConfigField("int", "CANONICAL_VERSION_CODE", "$canonicalVersionCode")
    buildConfigField("String", "DEFAULT_CURRENCIES", "\"EUR,AUD,GBP,CAD,CNY\"")
    buildConfigField("String", "GIPHY_API_KEY", "\"3o6ZsYH6U6Eri53TXy\"")
    buildConfigField("String", "SIGNAL_CAPTCHA_URL", "\"https://signalcaptchas.org/registration/generate.html\"")
    buildConfigField("String", "RECAPTCHA_PROOF_URL", "\"https://signalcaptchas.org/challenge/generate.html\"")

    buildConfigField("String", "BUILD_DISTRIBUTION_TYPE", "\"unset\"")
    buildConfigField("String", "BUILD_ENVIRONMENT_TYPE", "\"unset\"")
    buildConfigField("String", "BUILD_VARIANT_TYPE", "\"unset\"")
    buildConfigField("String", "BADGE_STATIC_ROOT", "\"https://updates2.signal.org/static/badges/\"")
    buildConfigField("String", "STRIPE_PUBLISHABLE_KEY", "\"pk_live_6cmGZopuTsV8novGgJJW9JpC00vLIgtQ1D\"")
    buildConfigField("boolean", "TRACING_ENABLED", "false")

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
      if (keystores["debug"] != null) {
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
        "proguard/proguard-webrtc.pro",
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
      val apkUpdateManifestUrl = if (file("${project.rootDir}/nightly-url.txt").exists()) {
        file("${project.rootDir}/nightly-url.txt").readText().trim()
      } else {
        "<unset>"
      }

      dimension = "distribution"
      versionNameSuffix = "-nightly-untagged-${getDateSuffix()}"
      buildConfigField("boolean", "MANAGES_APP_UPDATES", "true")
      buildConfigField("String", "APK_UPDATE_MANIFEST_URL", "\"${apkUpdateManifestUrl}\"")
      buildConfigField("String", "BUILD_DISTRIBUTION_TYPE", "\"nightly\"")
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
      buildConfigField("String", "SIGNAL_KEY_BACKUP_URL", "\"https://api-staging.backup.signal.org\"")
      buildConfigField("String", "SIGNAL_SVR2_URL", "\"https://svr2.staging.signal.org\"")
      buildConfigField("String", "SVR2_MRENCLAVE_DEPRECATED", "\"a8a261420a6bb9b61aa25bf8a79e8bd20d7652531feb3381cbffd446d270be95\"")
      buildConfigField("String", "SVR2_MRENCLAVE", "\"acb1973aa0bbbd14b3b4e06f145497d948fd4a98efc500fcce363b3b743ec482\"")
      buildConfigField("String", "UNIDENTIFIED_SENDER_TRUST_ROOT", "\"BbqY1DzohE4NUZoVF+L18oUPrK3kILllLEJh2UnPSsEx\"")
      buildConfigField("String", "ZKGROUP_SERVER_PUBLIC_PARAMS", "\"ABSY21VckQcbSXVNCGRYJcfWHiAMZmpTtTELcDmxgdFbtp/bWsSxZdMKzfCp8rvIs8ocCU3B37fT3r4Mi5qAemeGeR2X+/YmOGR5ofui7tD5mDQfstAI9i+4WpMtIe8KC3wU5w3Inq3uNWVmoGtpKndsNfwJrCg0Hd9zmObhypUnSkfYn2ooMOOnBpfdanRtrvetZUayDMSC5iSRcXKpdlukrpzzsCIvEwjwQlJYVPOQPj4V0F4UXXBdHSLK05uoPBCQG8G9rYIGedYsClJXnbrgGYG3eMTG5hnx4X4ntARBgELuMWWUEEfSK0mjXg+/2lPmWcTZWR9nkqgQQP0tbzuiPm74H2wMO4u1Wafe+UwyIlIT9L7KLS19Aw8r4sPrXZSSsOZ6s7M1+rTJN0bI5CKY2PX29y5Ok3jSWufIKcgKOnWoP67d5b2du2ZVJjpjfibNIHbT/cegy/sBLoFwtHogVYUewANUAXIaMPyCLRArsKhfJ5wBtTminG/PAvuBdJ70Z/bXVPf8TVsR292zQ65xwvWTejROW6AZX6aqucUjlENAErBme1YHmOSpU6tr6doJ66dPzVAWIanmO/5mgjNEDeK7DDqQdB1xd03HT2Qs2TxY3kCK8aAb/0iM0HQiXjxZ9HIgYhbtvGEnDKW5ILSUydqH/KBhW4Pb0jZWnqN/YgbWDKeJxnDbYcUob5ZY5Lt5ZCMKuaGUvCJRrCtuugSMaqjowCGRempsDdJEt+cMaalhZ6gczklJB/IbdwENW9KeVFPoFNFzhxWUIS5ML9riVYhAtE6JE5jX0xiHNVIIPthb458cfA8daR0nYfYAUKogQArm0iBezOO+mPk5vCM=\"")
      buildConfigField("String", "GENERIC_SERVER_PUBLIC_PARAMS", "\"AHILOIrFPXX9laLbalbA9+L1CXpSbM/bTJXZGZiuyK1JaI6dK5FHHWL6tWxmHKYAZTSYmElmJ5z2A5YcirjO/yfoemE03FItyaf8W1fE4p14hzb5qnrmfXUSiAIVrhaXVwIwSzH6RL/+EO8jFIjJ/YfExfJ8aBl48CKHgu1+A6kWynhttonvWWx6h7924mIzW0Czj2ROuh4LwQyZypex4GuOPW8sgIT21KNZaafgg+KbV7XM1x1tF3XA17B4uGUaDbDw2O+nR1+U5p6qHPzmJ7ggFjSN6Utu+35dS1sS0P9N\"")
      buildConfigField("String", "BACKUP_SERVER_PUBLIC_PARAMS", "\"AHYrGb9IfugAAJiPKp+mdXUx+OL9zBolPYHYQz6GI1gWjpEu5me3zVNSvmYY4zWboZHif+HG1sDHSuvwFd0QszSwuSF4X4kRP3fJREdTZ5MCR0n55zUppTwfHRW2S4sdQ0JGz7YDQIJCufYSKh0pGNEHL6hv79Agrdnr4momr3oXdnkpVBIp3HWAQ6IbXQVSG18X36GaicI1vdT0UFmTwU2KTneluC2eyL9c5ff8PcmiS+YcLzh0OKYQXB5ZfQ06d6DiINvDQLy75zcfUOniLAj0lGJiHxGczin/RXisKSR8\"")
      buildConfigField("String", "MOBILE_COIN_ENVIRONMENT", "\"testnet\"")
      buildConfigField("String", "SIGNAL_CAPTCHA_URL", "\"https://signalcaptchas.org/staging/registration/generate.html\"")
      buildConfigField("String", "RECAPTCHA_PROOF_URL", "\"https://signalcaptchas.org/staging/challenge/generate.html\"")

      buildConfigField("String", "BUILD_ENVIRONMENT_TYPE", "\"Staging\"")
      buildConfigField("String", "STRIPE_PUBLISHABLE_KEY", "\"pk_test_sngOd8FnXNkpce9nPXawKrJD00kIDngZkD\"")
    }

    create("pnp") {
      dimension = "environment"

      initWith(getByName("staging"))
      applicationIdSuffix = ".pnp"

      buildConfigField("String", "BUILD_ENVIRONMENT_TYPE", "\"Pnp\"")
    }
  }

  lint {
    abortOnError = true
    baseline = file("lint-baseline.xml")
    checkReleaseBuilds = false
    disable += "LintError"
  }

  applicationVariants.all {
    outputs
      .map { it as com.android.build.gradle.internal.api.ApkVariantOutputImpl }
      .forEach { output ->
        if (output.baseName.contains("nightly")) {
          output.versionCodeOverride = canonicalVersionCode * postFixSize + 5
          var tag = getCurrentGitTag()
          if (!tag.isNullOrEmpty()) {
            if (tag.startsWith("v")) {
              tag = tag.substring(1)
            }
            output.versionNameOverride = tag
            output.outputFileName = output.outputFileName.replace(".apk", "-${output.versionNameOverride}.apk")
          } else {
            output.outputFileName = output.outputFileName.replace(".apk", "-$versionName.apk")
          }
        } else {
          output.outputFileName = output.outputFileName.replace(".apk", "-$versionName.apk")

          val abiName: String = output.getFilter("ABI") ?: "universal"
          val postFix: Int = abiPostFix[abiName]!!

          if (postFix >= postFixSize) {
            throw AssertionError("postFix is too large")
          }

          output.versionCodeOverride = canonicalVersionCode * postFixSize + postFix
        }
      }
  }

  androidComponents {
    beforeVariants { variant ->
      variant.enable = variant.name in selectableVariants
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

  implementation(project(":libsignal-service"))
  implementation(project(":paging"))
  implementation(project(":core-util"))
  implementation(project(":glide-config"))
  implementation(project(":video"))
  implementation(project(":device-transfer"))
  implementation(project(":image-editor"))
  implementation(project(":donations"))
  implementation(project(":contacts"))
  implementation(project(":qr"))
  implementation(project(":sticky-header-grid"))
  implementation(project(":photoview"))
  implementation(project(":core-ui"))

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
  implementation(libs.androidx.multidex)
  implementation(libs.androidx.navigation.fragment.ktx)
  implementation(libs.androidx.navigation.ui.ktx)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.lifecycle.viewmodel.ktx)
  implementation(libs.androidx.lifecycle.livedata.ktx)
  implementation(libs.androidx.lifecycle.process)
  implementation(libs.androidx.lifecycle.viewmodel.savedstate)
  implementation(libs.androidx.lifecycle.common.java8)
  implementation(libs.androidx.lifecycle.reactivestreams.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)
  implementation(libs.androidx.concurrent.futures)
  implementation(libs.androidx.autofill)
  implementation(libs.androidx.biometric)
  implementation(libs.androidx.sharetarget)
  implementation(libs.androidx.profileinstaller)
  implementation(libs.androidx.asynclayoutinflater)
  implementation(libs.androidx.asynclayoutinflater.appcompat)
  implementation(libs.firebase.messaging) {
    exclude(group = "com.google.firebase", module = "firebase-core")
    exclude(group = "com.google.firebase", module = "firebase-analytics")
    exclude(group = "com.google.firebase", module = "firebase-measurement-connector")
  }
  implementation(libs.google.play.services.maps)
  implementation(libs.google.play.services.auth)
  implementation(libs.bundles.media3)
  implementation(libs.conscrypt.android)
  implementation(libs.signal.aesgcmprovider)
  implementation(libs.libsignal.android)
  implementation(libs.mobilecoin)
  implementation(libs.signal.ringrtc)
  implementation(libs.leolin.shortcutbadger)
  implementation(libs.emilsjolander.stickylistheaders)
  implementation(libs.apache.httpclient.android)
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
  implementation(libs.android.smsmms) {
    exclude(group = "com.squareup.okhttp", module = "okhttp")
    exclude(group = "com.squareup.okhttp", module = "okhttp-urlconnection")
  }
  implementation(libs.stream)
  implementation(libs.lottie)
  implementation(libs.signal.android.database.sqlcipher)
  implementation(libs.androidx.sqlite)
  implementation(libs.google.ez.vcard) {
    exclude(group = "com.fasterxml.jackson.core")
    exclude(group = "org.freemarker")
  }
  implementation(libs.dnsjava)
  implementation(libs.kotlinx.collections.immutable)
  implementation(libs.accompanist.permissions)
  implementation(libs.kotlin.stdlib.jdk8)
  implementation(libs.kotlin.reflect)
  implementation(libs.jackson.module.kotlin)
  implementation(libs.rxjava3.rxandroid)
  implementation(libs.rxjava3.rxkotlin)
  implementation(libs.rxdogtag)

  "spinnerImplementation"(project(":spinner"))

  "canaryImplementation"(libs.square.leakcanary)

  "instrumentationImplementation"(libs.androidx.fragment.testing) {
    exclude(group = "androidx.test", module = "core")
  }

  testImplementation(testLibs.junit.junit)
  testImplementation(testLibs.assertj.core)
  testImplementation(testLibs.mockito.core)
  testImplementation(testLibs.mockito.kotlin)
  testImplementation(testLibs.androidx.test.core)
  testImplementation(testLibs.robolectric.robolectric) {
    exclude(group = "com.google.protobuf", module = "protobuf-java")
  }
  testImplementation(testLibs.robolectric.shadows.multidex)
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
  testImplementation(testLibs.hamcrest.hamcrest)
  testImplementation(testLibs.mockk)
  testImplementation(testFixtures(project(":libsignal-service")))
  testImplementation(testLibs.espresso.core)

  androidTestImplementation(testLibs.androidx.test.ext.junit)
  androidTestImplementation(testLibs.espresso.core)
  androidTestImplementation(testLibs.androidx.test.core)
  androidTestImplementation(testLibs.androidx.test.core.ktx)
  androidTestImplementation(testLibs.androidx.test.ext.junit.ktx)
  androidTestImplementation(testLibs.mockito.android)
  androidTestImplementation(testLibs.mockito.kotlin)
  androidTestImplementation(testLibs.mockk.android)
  androidTestImplementation(testLibs.square.okhttp.mockserver)

  androidTestUtil(testLibs.androidx.test.orchestrator)
}

fun assertIsGitRepo() {
  if (!file("${project.rootDir}/.git").exists()) {
    throw IllegalStateException("Must be a git repository to guarantee reproducible builds! (git hash is part of APK)")
  }
}

fun getLastCommitTimestamp(): String {
  assertIsGitRepo()

  ByteArrayOutputStream().use { os ->
    exec {
      executable = "git"
      args = listOf("log", "-1", "--pretty=format:%ct")
      standardOutput = os
    }

    return os.toString() + "000"
  }
}

fun getGitHash(): String {
  assertIsGitRepo()

  val stdout = ByteArrayOutputStream()
  exec {
    commandLine = listOf("git", "rev-parse", "HEAD")
    standardOutput = stdout
  }

  return stdout.toString().trim().substring(0, 12)
}

fun getCurrentGitTag(): String? {
  assertIsGitRepo()

  val stdout = ByteArrayOutputStream()
  exec {
    commandLine = listOf("git", "tag", "--points-at", "HEAD")
    standardOutput = stdout
  }

  val output: String = stdout.toString().trim()

  return if (output.isNotEmpty()) {
    val tags = output.split("\n").toList()
    tags.firstOrNull { it.contains("nightly") } ?: tags[0]
  } else {
    null
  }
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

project.tasks.configureEach {
  if (name.lowercase().contains("nightly") && name != "checkNightlyParams") {
    dependsOn(tasks.getByName("checkNightlyParams"))
  }
}

tasks.register("checkNightlyParams") {
  doFirst {
    if (project.gradle.startParameter.taskNames.any { it.lowercase().contains("nightly") }) {

      if (!file("${project.rootDir}/nightly-url.txt").exists()) {
        throw GradleException("Cannot find 'nightly-url.txt' for nightly build! It must exist in the root of this project and contain the location of the nightly manifest.")
      }
    }
  }
}

fun loadKeystoreProperties(filename: String): Properties? {
  val keystorePropertiesFile = file("${project.rootDir}/$filename")

  return if (keystorePropertiesFile.exists()) {
    val keystoreProperties = Properties()
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
    keystoreProperties
  } else {
    null
  }
}

fun getDateSuffix(): String {
  return SimpleDateFormat("yyyy-MM-dd-HH:mm").format(Date())
}

fun getMapsKey(): String {
  val mapKey = file("${project.rootDir}/maps.key")

  return if (mapKey.exists()) {
    mapKey.readLines()[0]
  } else {
    "AIzaSyCSx9xea86GwDKGznCAULE9Y5a8b-TfN9U"
  }
}

fun Project.languageList(): List<String> {
  return fileTree("src/main/res") { include("**/strings.xml") }
    .map { stringFile -> stringFile.parentFile.name }
    .map { valuesFolderName -> valuesFolderName.replace("values-", "") }
    .filter { valuesFolderName -> valuesFolderName != "values" }
    .map { languageCode -> languageCode.replace("-r", "_") }
    .distinct() + "en"
}

fun String.capitalize(): String {
  return this.replaceFirstChar { it.uppercase() }
}
