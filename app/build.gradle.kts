@file:Suppress("UnstableApiUsage")

import com.android.build.api.artifact.ArtifactTransformationRequest
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.HasAndroidTest
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.Properties
import javax.inject.Inject

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.ktlint)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlinx.serialization)
  alias(testLibs.plugins.compose.screenshot)
  alias(benchmarkLibs.plugins.baselineprofile)
  id("androidx.navigation.safeargs")
  id("kotlin-parcelize")
  id("com.squareup.wire")
  id("translations")
  id("licenses")
}

val staticIps = Properties().apply { file("static-ips.properties").reader().use { load(it) } }
staticIps.stringPropertyNames().forEach { rootProject.extra[it] = staticIps.getProperty(it) }

val canonicalVersionCode = 1720
val canonicalVersionName = "8.20.0"
val currentHotfixVersion = 0
val maxHotfixVersions = 100

// We don't want versions to ever end in 0 so that they don't conflict with nightly versions
val possibleHotfixVersions = (0 until maxHotfixVersions).toList().filter { it % 10 != 0 }

val debugKeystorePropertiesProvider: Provider<Properties> = providers.of(PropertiesFileValueSource::class.java) {
  parameters.file.set(rootProject.layout.projectDirectory.file("keystore.debug.properties"))
}

val languagesProvider: Provider<List<String>> = providers.of(LanguageListValueSource::class.java) {
  parameters.resDir.set(layout.projectDirectory.dir("src/main/res"))
}

val languagesForBuildConfigProvider = languagesProvider.map { languages ->
  languages.joinToString(separator = ", ") { language -> "\"$language\"" }
}

val localPropertiesFile = File(rootProject.projectDir, "local.properties")
val localProperties: Properties? = if (localPropertiesFile.exists()) {
  Properties().apply { localPropertiesFile.inputStream().use { load(it) } }
} else {
  null
}
val quickstartCredentialsDir: String? = localProperties?.getProperty("quickstart.credentials.dir")
val benchmarkBackupFile: String? = localProperties?.getProperty("benchmark.backup.file")

val isInstrumentationTestRun = gradle.startParameter.taskNames.any { taskName ->
  val lower = taskName.lowercase()
  lower.contains("androidtest") || lower.contains("connectedcheck")
}

val selectableVariants = listOf(
  "nightlyProdSpinner",
  "nightlyProdPerf",
  "nightlyProdRelease",
  "nightlyStagingRelease",
  "playProdDebug",
  "playProdSpinner",
  "playProdCanary",
  "playProdPerf",
  "playProdMocked",
  "playProdNonMinifiedMocked",
  "playProdBenchmark",
  "playProdRelease",
  "playStagingDebug",
  "playStagingCanary",
  "playStagingSpinner",
  "playStagingPerf",
  "playStagingRelease",
  "playProdQuickstart",
  "playStagingQuickstart",
  "websiteProdSpinner",
  "websiteProdRelease",
  "githubProdSpinner",
  "githubProdRelease"
)

// Wire 5.x iterates Android source sets and expects matching Kotlin source sets.
// AGP 9.0's built-in Kotlin doesn't create all source sets automatically.
val kotlinExt = extensions.getByName("kotlin") as KotlinAndroidProjectExtension
android.sourceSets.all {
  kotlinExt.sourceSets.findByName(name) ?: kotlinExt.sourceSets.create(name)
}
// AGP 9.0's built-in Kotlin doesn't pick up extra java.srcDir entries from Android
// source sets, so add shared dirs directly to the relevant Kotlin compile tasks.
tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java).configureEach {
  val isTestTask = name.contains("UnitTest") || name.contains("AndroidTest")
  if (isTestTask) {
    source("$projectDir/src/testShared")
  }
  if (!isTestTask && (name.contains("Mocked") || name.contains("Benchmark"))) {
    source("$projectDir/src/benchmarkShared/java")
  }
  if (isTestTask && name.contains("AndroidTest")) {
    source("$projectDir/src/benchmarkShared/java")
  }
}

wire {
  kotlin {
    javaInterop = true
  }

  sourcePath {
    srcDir("src/main/protowire")
  }

  protoPath {
    srcDir("${project.rootDir}/lib/libsignal-service/src/main/protowire")
    srcDir("${project.rootDir}/lib/archive/src/main/protowire")
  }
}

ktlint {
  version.set("1.5.0")
}

// ktlint only scans convention source dirs, so the shared dirs added to the compile tasks are
// otherwise skipped. Add them to the base test/androidTest ktlint tasks so ktlintCheck/format cover them.
tasks.withType(org.jlleitschuh.gradle.ktlint.tasks.BaseKtLintCheckTask::class.java).configureEach {
  if (name.endsWith("OverTestSourceSet") || name.endsWith("OverAndroidTestSourceSet")) {
    source("$projectDir/src/testShared")
  }
  if (name.endsWith("OverAndroidTestSourceSet")) {
    source("$projectDir/src/benchmarkShared/java")
  }
}

screenshotTests {
  // Fraction of differing pixels tolerated before a screenshot test fails (0.0001 = 0.01%).
  imageDifferenceThreshold = 0.0001f
}

android {
  namespace = "org.thoughtcrime.securesms"

  experimentalProperties["android.experimental.enableScreenshotTest"] = true

  buildToolsVersion = libs.versions.buildTools.get()
  compileSdkVersion(libs.versions.compileSdk.get())
  ndkVersion = libs.versions.ndk.get()

  flavorDimensions += listOf("distribution", "environment")

  android.bundle.language.enableSplit = false

  debugKeystorePropertiesProvider.get().takeIf { it.isNotEmpty() }?.let { properties ->
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
      localDevices {
        create("pixel3api30") {
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
      java.srcDir("$projectDir/src/benchmarkShared/java")
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
        "**/*.dll",
        "**/*.proto"
      )
    }
  }

  buildFeatures {
    buildConfig = true
    viewBinding = true
    compose = true
  }

  defaultConfig {
    if (currentHotfixVersion >= maxHotfixVersions) {
      throw AssertionError("Hotfix version offset is too large!")
    }
    versionCode = (canonicalVersionCode * maxHotfixVersions) + possibleHotfixVersions[currentHotfixVersion]
    versionName = canonicalVersionName

    if (isInstrumentationTestRun) {
      applicationIdSuffix = ".test_run"
    }

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
    buildConfigField("String", "SVR2_MRENCLAVE_LEGACY", "\"1240acbd4aa26974184844c8a46b1022d3957ac8a76c1fd8f5b1a15141ee0708\"")
    buildConfigField("String", "SVR2_MRENCLAVE", "\"ced8217b26228e4b210c985786999d095c4958a94faf37b14acaf25c4cbb02a4\"")
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

    testInstrumentationRunner = if (project.hasProperty("imoTests")) {
      "org.thoughtcrime.securesms.testing.incomingmessageobserver.IncomingMessageObserverTestRunner"
    } else {
      "org.thoughtcrime.securesms.testing.SignalTestRunner"
    }
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
        getDefaultProguardFile("proguard-android-optimize.txt"),
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
        "proguard/proguard-dnsjava.pro",
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
      applicationIdSuffix = ".benchmark"

      buildConfigField("String", "BUILD_VARIANT_TYPE", "\"Benchmark\"")
      buildConfigField("boolean", "TRACING_ENABLED", "true")
      buildConfigField("String[]", "UNIDENTIFIED_SENDER_TRUST_ROOTS", "new String[]{ \"BVT/2gHqbrG1xzuIypLIOjFgMtihrMld1/5TGADL6Dhv\"}")

      manifestPlaceholders["applicationClass"] = "org.thoughtcrime.securesms.BenchmarkApplicationContext"
    }

    create("mocked") {
      initWith(getByName("debug"))
      isDefault = false
      isDebuggable = false
      isMinifyEnabled = true
      matchingFallbacks += "debug"
      buildConfigField("String", "BUILD_VARIANT_TYPE", "\"Benchmark\"")
      buildConfigField("boolean", "TRACING_ENABLED", "true")

      manifestPlaceholders["applicationClass"] = "org.thoughtcrime.securesms.ApplicationContext"
    }

    create("canary") {
      initWith(getByName("debug"))
      isDefault = false
      isMinifyEnabled = false
      matchingFallbacks += "debug"
      buildConfigField("String", "BUILD_VARIANT_TYPE", "\"Canary\"")
    }

    create("quickstart") {
      initWith(getByName("debug"))
      isDefault = false
      isMinifyEnabled = false
      matchingFallbacks += "debug"
      buildConfigField("String", "BUILD_VARIANT_TYPE", "\"Quickstart\"")
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

    create("github") {
      dimension = "distribution"
      buildConfigField("boolean", "MANAGES_APP_UPDATES", "false")
      buildConfigField("String", "APK_UPDATE_MANIFEST_URL", "null")
      buildConfigField("String", "BUILD_DISTRIBUTION_TYPE", "\"github\"")
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
      buildConfigField("String", "SVR2_MRENCLAVE_LEGACY", "\"97f151f6ed078edbbfd72fa9cae694dcc08353f1f5e8d9ccd79a971b10ffc535\"")
      buildConfigField("String", "SVR2_MRENCLAVE", "\"3c699f4975aaa3d172c0aad042f94f031b2b03e10b9c19a45116a01693d83302\"")
      buildConfigField("String[]", "UNIDENTIFIED_SENDER_TRUST_ROOTS", "new String[]{\"BbqY1DzohE4NUZoVF+L18oUPrK3kILllLEJh2UnPSsEx\", \"BYhU6tPjqP46KGZEzRs1OL4U39V5dlPJ/X09ha4rErkm\"}")
      buildConfigField("String", "ZKGROUP_SERVER_PUBLIC_PARAMS", "\"ABSY21VckQcbSXVNCGRYJcfWHiAMZmpTtTELcDmxgdFbtp/bWsSxZdMKzfCp8rvIs8ocCU3B37fT3r4Mi5qAemeGeR2X+/YmOGR5ofui7tD5mDQfstAI9i+4WpMtIe8KC3wU5w3Inq3uNWVmoGtpKndsNfwJrCg0Hd9zmObhypUnSkfYn2ooMOOnBpfdanRtrvetZUayDMSC5iSRcXKpdlukrpzzsCIvEwjwQlJYVPOQPj4V0F4UXXBdHSLK05uoPBCQG8G9rYIGedYsClJXnbrgGYG3eMTG5hnx4X4ntARBgELuMWWUEEfSK0mjXg+/2lPmWcTZWR9nkqgQQP0tbzuiPm74H2wMO4u1Wafe+UwyIlIT9L7KLS19Aw8r4sPrXZSSsOZ6s7M1+rTJN0bI5CKY2PX29y5Ok3jSWufIKcgKOnWoP67d5b2du2ZVJjpjfibNIHbT/cegy/sBLoFwtHogVYUewANUAXIaMPyCLRArsKhfJ5wBtTminG/PAvuBdJ70Z/bXVPf8TVsR292zQ65xwvWTejROW6AZX6aqucUjlENAErBme1YHmOSpU6tr6doJ66dPzVAWIanmO/5mgjNEDeK7DDqQdB1xd03HT2Qs2TxY3kCK8aAb/0iM0HQiXjxZ9HIgYhbtvGEnDKW5ILSUydqH/KBhW4Pb0jZWnqN/YgbWDKeJxnDbYcUob5ZY5Lt5ZCMKuaGUvCJRrCtuugSMaqjowCGRempsDdJEt+cMaalhZ6gczklJB/IbdwENW9KeVFPoFNFzhxWUIS5ML9riVYhAtE6JE5jX0xiHNVIIPthb458cfA8daR0nYfYAUKogQArm0iBezOO+mPk5vCNWI+wwkyFCqNDXz/qxl1gAntuCJtSfq9OC3NkdhQlgYQ==\"")
      buildConfigField("String", "GENERIC_SERVER_PUBLIC_PARAMS", "\"AHILOIrFPXX9laLbalbA9+L1CXpSbM/bTJXZGZiuyK1JaI6dK5FHHWL6tWxmHKYAZTSYmElmJ5z2A5YcirjO/yfoemE03FItyaf8W1fE4p14hzb5qnrmfXUSiAIVrhaXVwIwSzH6RL/+EO8jFIjJ/YfExfJ8aBl48CKHgu1+A6kWynhttonvWWx6h7924mIzW0Czj2ROuh4LwQyZypex4GuOPW8sgIT21KNZaafgg+KbV7XM1x1tF3XA17B4uGUaDbDw2O+nR1+U5p6qHPzmJ7ggFjSN6Utu+35dS1sS0P9N\"")
      buildConfigField("String", "BACKUP_SERVER_PUBLIC_PARAMS", "\"AHYrGb9IfugAAJiPKp+mdXUx+OL9zBolPYHYQz6GI1gWjpEu5me3zVNSvmYY4zWboZHif+HG1sDHSuvwFd0QszSwuSF4X4kRP3fJREdTZ5MCR0n55zUppTwfHRW2S4sdQ0JGz7YDQIJCufYSKh0pGNEHL6hv79Agrdnr4momr3oXdnkpVBIp3HWAQ6IbXQVSG18X36GaicI1vdT0UFmTwU2KTneluC2eyL9c5ff8PcmiS+YcLzh0OKYQXB5ZfQ06d6DiINvDQLy75zcfUOniLAj0lGJiHxGczin/RXisKSR8\"")
      buildConfigField("String", "MOBILE_COIN_ENVIRONMENT", "\"testnet\"")
      buildConfigField("String", "SIGNAL_CAPTCHA_URL", "\"https://signalcaptchas.org/staging/registration/generate.html\"")
      buildConfigField("String", "RECAPTCHA_PROOF_URL", "\"https://signalcaptchas.org/staging/challenge/generate.html\"")
      buildConfigField("org.signal.libsignal.net.Network.Environment", "LIBSIGNAL_NET_ENV", "org.signal.libsignal.net.Network.Environment.STAGING")
      buildConfigField("int", "LIBSIGNAL_LOG_LEVEL", "org.signal.libsignal.protocol.logging.SignalProtocolLogger.DEBUG")

      buildConfigField("String", "BUILD_ENVIRONMENT_TYPE", "\"Staging\"")
      buildConfigField("String", "STRIPE_PUBLISHABLE_KEY", "\"pk_test_sngOd8FnXNkpce9nPXawKrJD00kIDngZkD\"")
    }
  }

  lint {
    abortOnError = true
    baseline = file("lint-baseline.xml")
    checkReleaseBuilds = false
    ignoreWarnings = true
    quiet = true
    disable += "LintError"
    lintConfig = rootProject.file("lint.xml")
  }

  val releaseDir = "$projectDir/src/release/java"
  val debugDir = "$projectDir/src/debug/java"

  android.buildTypes.configureEach {
    val path = if (name == "release") releaseDir else debugDir
    sourceSets.named(name) {
      java.srcDir(path)
    }
  }

  sourceSets {
    getByName("mocked") {
      java.srcDir("$projectDir/src/benchmarkShared/java")
      manifest.srcFile("$projectDir/src/benchmarkShared/AndroidManifest.xml")
    }

    getByName("benchmark") {
      java.srcDir("$projectDir/src/benchmarkShared/java")
      manifest.srcFile("$projectDir/src/benchmarkShared/AndroidManifest.xml")
    }
  }
}

androidComponents {
  beforeVariants { variant ->
    variant.enable = variant.name in selectableVariants
    if (variant.enable) {
      (variant as? com.android.build.api.variant.HasUnitTestBuilder)?.enableUnitTest = true
    }
  }
  onVariants(selector().all()) { variant: com.android.build.api.variant.ApplicationVariant ->
    // Rename APK to include version name
    val renameTask = tasks.register<RenameApkTask>("renameApk${variant.name.replaceFirstChar { it.uppercase() }}")
    val renameRequest = variant.artifacts.use(renameTask)
      .wiredWithDirectories(RenameApkTask::apkFolder, RenameApkTask::outFolder)
      .toTransformMany(SingleArtifact.APK)
    renameTask.configure {
      transformationRequest.set(renameRequest)
    }

    // Include the test-only library on non-release builds.
    if (variant.buildType == "release") {
      variant.packaging.jniLibs.excludes.add("**/libsignal_jni_testing.so")
      variant.androidResources.ignoreAssetsPatterns.add("libsignal-testing.md")
    }

    // Starting with minSdk 23, Android leaves native libraries uncompressed, which is fine for the Play Store, but not for our self-distributed APKs.
    // This reverts it to the legacy behavior, compressing the native libraries, and drastically reducing the APK file size.
    if (variant.name.contains("website", ignoreCase = true) || variant.name.contains("github", ignoreCase = true)) {
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
          output.versionName.set("$tag | ${getLastCommitDateTimeUtc()}")
          output.versionCode.set(nightlyVersionCode)
        }
      }
    }
  }

  onVariants(selector().withBuildType("quickstart")) { variant ->
    val environment = variant.flavorName?.let { name ->
      when {
        name.contains("staging", ignoreCase = true) -> "staging"
        name.contains("prod", ignoreCase = true) -> "prod"
        else -> "prod"
      }
    } ?: "prod"

    val taskProvider = tasks.register<CopyQuickstartCredentialsTask>("copyQuickstartCredentials${variant.name.capitalize()}") {
      if (quickstartCredentialsDir != null) {
        inputDir.set(File(quickstartCredentialsDir))
      }
      filePrefix.set("${environment}_")
    }
    variant.sources.assets?.addGeneratedSourceDirectory(taskProvider) { it.outputDir }
  }

  onVariants(selector().withBuildType("benchmark")) { variant ->
    val taskProvider = tasks.register<CopyBenchmarkBackupTask>("copyBenchmarkBackup${variant.name.capitalize()}") {
      if (benchmarkBackupFile != null) {
        inputFile.set(File(benchmarkBackupFile))
      }
    }
    variant.sources.assets?.addGeneratedSourceDirectory(taskProvider) { it.outputDir }
  }

  onVariants(selector().withName("playProdDebug")) { variant ->
    val androidTest = (variant as? HasAndroidTest)?.androidTest ?: return@onVariants

    tasks.register<FirebaseTestLabTask>("firebaseTestLab") {
      group = "Verification"
      description = "Runs the ${variant.name} instrumentation tests on Firebase Test Lab via the gcloud CLI. Run a single class with -Pftl.class=<fqcn>[#method]; override other defaults with -Pftl.* properties."

      appApkDirectory.set(variant.artifacts.get(SingleArtifact.APK))
      testApkDirectory.set(androidTest.artifacts.get(SingleArtifact.APK))

      val deviceOverride = project.providers.gradleProperty("ftl.devices").orNull
      devices.set(
        deviceOverride?.split(";")?.map { it.trim() }?.filter { it.isNotEmpty() }
          ?: listOf("model=Pixel2.arm,version=31,locale=en,orientation=portrait")
      )

      useOrchestrator.set(true)
      environmentVariables.set(mapOf("clearPackageData" to "true"))
      testTimeout.set(project.providers.gradleProperty("ftl.timeout").getOrElse("30m"))
      numFlakyTestAttempts.set(project.providers.gradleProperty("ftl.numFlakyTestAttempts").map { it.toInt() }.getOrElse(1))
      gcloudProject.set(project.providers.gradleProperty("ftl.project"))
      resultsBucket.set(project.providers.gradleProperty("ftl.resultsBucket"))
      resultsDir.set(project.providers.gradleProperty("ftl.resultsDir"))

      val testClass = project.providers.gradleProperty("ftl.class").orNull?.takeIf { it.isNotBlank() }
      testTargets.set(
        if (testClass != null) "class $testClass" else project.providers.gradleProperty("ftl.testTargets").orNull
      )
      gcloudExecutable.set(project.providers.gradleProperty("ftl.gcloud").getOrElse("gcloud"))
      extraArgs.set(
        project.providers.gradleProperty("ftl.extraArgs").orNull
          ?.split(" ")?.map { it.trim() }?.filter { it.isNotEmpty() }
          ?: emptyList()
      )
    }
  }
}

baselineProfile {
  warnings {
    disabledVariants = false
  }

  mergeIntoMain = true

  variants.create("mocked") {
    from(project(":baseline-profile"))
  }

  dexLayoutOptimization = false
}

kotlin {
  compilerOptions {
    jvmTarget = JvmTarget.fromTarget(libs.versions.kotlinJvmTarget.get())
    freeCompilerArgs.addAll("-Xjvm-default=all")
    suppressWarnings = true
  }
}

dependencies {
  lintChecks(project(":lintchecks"))
  ktlintRuleset(libs.ktlint.twitter.compose)
  coreLibraryDesugaring(libs.android.tools.desugar)

  implementation(project(":lib:archive"))
  implementation(project(":lib:libsignal-service"))
  implementation(project(":lib:network"))
  implementation(project(":lib:paging"))
  implementation(project(":core:util"))
  implementation(project(":lib:glide"))
  implementation(project(":lib:video"))
  implementation(project(":lib:device-transfer"))
  implementation(project(":lib:image-editor"))
  implementation(project(":lib:donations"))
  implementation(project(":lib:debuglogs-viewer"))
  implementation(project(":lib:contacts"))
  implementation(project(":lib:qr"))
  implementation(project(":lib:sticky-header-grid"))
  implementation(project(":lib:photoview"))
  implementation(project(":lib:blurhash"))
  implementation(project(":core:ui"))
  implementation(project(":core:models"))
  implementation(project(":core:models-jvm"))
  implementation(project(":feature:camera"))
  implementation(project(":feature:registration"))
  implementation(project(":lib:apng"))

  implementation(libs.androidx.fragment.ktx)
  implementation(libs.androidx.appcompat)
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
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)
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
  implementation(libs.androidx.core.telecom)
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
  implementation(libs.androidx.media)
  implementation(libs.bundles.media3)
  implementation(libs.conscrypt.android)
  implementation(libs.signal.aesgcmprovider)
  implementation(libs.libsignal.android)
  implementation(libs.mobilecoin)
  implementation(libs.signal.ringrtc)
  implementation(libs.leolin.shortcutbadger)
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
  implementation(libs.lottie)
  implementation(libs.lottie.compose)

  // Compose screenshot testing
  screenshotTestImplementation(testLibs.compose.screenshot.validation.api)
  screenshotTestImplementation(libs.androidx.compose.ui.tooling.core)
  screenshotTestImplementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.signal.android.database.sqlcipher)
  implementation(libs.androidx.sqlite)
  testImplementation(libs.androidx.sqlite.framework)
  implementation(libs.google.ez.vcard) {
    exclude(group = "com.fasterxml.jackson.core")
    exclude(group = "org.freemarker")
  }
  implementation(libs.dnsjava)
  implementation(libs.kotlinx.collections.immutable)
  implementation(libs.arrow.core)
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
  implementation(project(":feature:media-send"))

  "spinnerImplementation"(project(":lib:spinner"))

  "canaryImplementation"(libs.square.leakcanary)

  androidTestImplementation(libs.androidx.fragment.testing) {
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
  testImplementation(testFixtures(project(":core:ui")))
  testImplementation(testFixtures(project(":lib:libsignal-service")))
  testImplementation(testLibs.espresso.core)
  testImplementation(testLibs.kotlinx.coroutines.test)
  testImplementation(testLibs.sqlite.jdbc)
  testImplementation(libs.androidx.compose.ui.test.junit4)

  "perfImplementation"(libs.androidx.compose.ui.test.manifest)

  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.compose.ui.test.manifest)
  androidTestImplementation(testLibs.androidx.test.ext.junit)
  androidTestImplementation(testLibs.espresso.core)
  androidTestImplementation(testLibs.espresso.contrib) {
    // espresso-contrib transitively pulls the full checkerframework jar (only its annotations are needed),
    // whose MANIFEST.MF collides with other test dependencies during androidTest resource merging.
    exclude(group = "org.checkerframework", module = "checker")
    // accessibility-test-framework drags in an ancient com.google.protobuf:protobuf-lite:3.0.1 whose
    // GeneratedMessageLite wins the merged dex and lacks registerDefaultInstance(Class, GeneratedMessageLite),
    // crashing tests at runtime. We only use RecyclerViewActions from contrib, not the accessibility checks.
    exclude(group = "com.google.android.apps.common.testing.accessibility.framework")
  }
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

fun getLastCommitDateTimeUtc(): String {
  val timestamp = providers.exec {
    commandLine("git", "log", "-1", "--pretty=format:%ct")
  }.standardOutput.asText.get().trim().toLong()
  val instant = Instant.ofEpochSecond(timestamp)
  val formatter = DateTimeFormatter.ofPattern("MMM d '@' HH:mm 'UTC'", Locale.US)
    .withZone(ZoneOffset.UTC)
  return formatter.format(instant)
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

abstract class PropertiesFileValueSource : ValueSource<Properties, PropertiesFileValueSource.Params> {
  interface Params : ValueSourceParameters {
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val file: RegularFileProperty
  }

  override fun obtain(): Properties {
    val f: File = parameters.file.asFile.get()
    if (!f.exists()) return Properties()

    return Properties().apply {
      f.inputStream().use { load(it) }
    }
  }
}

fun String.capitalize(): String {
  return this.replaceFirstChar { it.uppercase() }
}

abstract class CopyQuickstartCredentialsTask : DefaultTask() {
  @get:InputDirectory
  @get:Optional
  abstract val inputDir: DirectoryProperty

  @get:Input
  abstract val filePrefix: Property<String>

  @get:OutputDirectory
  abstract val outputDir: DirectoryProperty

  @TaskAction
  fun copy() {
    if (!inputDir.isPresent) {
      throw GradleException("quickstart.credentials.dir is not set in local.properties. This is required for quickstart builds.")
    }

    val prefix = filePrefix.get()
    val candidates = inputDir.get().asFile.listFiles()
      ?.filter { it.extension == "json" && it.name.startsWith(prefix) }
      ?: emptyList()

    if (candidates.isEmpty()) {
      throw GradleException("No credential files matching '$prefix*.json' found in ${inputDir.get().asFile}. Add files like '${prefix}account1.json' to your credentials directory.")
    }

    val chosen = candidates.random()
    logger.lifecycle("Selected quickstart credential: ${chosen.name}")

    val dest = outputDir.get().asFile.resolve("quickstart")
    dest.mkdirs()
    chosen.copyTo(dest.resolve(chosen.name), overwrite = true)
  }
}

abstract class CopyBenchmarkBackupTask : DefaultTask() {
  @get:InputFile
  @get:Optional
  abstract val inputFile: RegularFileProperty

  @get:OutputDirectory
  abstract val outputDir: DirectoryProperty

  @TaskAction
  fun copy() {
    val dest = outputDir.get().asFile.resolve("backups")
    dest.mkdirs()

    if (!inputFile.isPresent) {
      logger.lifecycle("benchmark.backup.file is not set in local.properties. Benchmark tests using backup data will crash at runtime.")
      return
    }

    val backupFile = inputFile.get().asFile
    logger.lifecycle("Using benchmark backup: ${backupFile.absolutePath} (${backupFile.length() / 1024}KB)")
    backupFile.copyTo(dest.resolve("backup.binproto"), overwrite = true)
  }
}

/**
 * Runs an instrumentation test suite on Firebase Test Lab by shelling out to `gcloud firebase test android run`.
 *
 * The `gcloud` CLI must be installed and authenticated (`gcloud auth login` and a configured project, or an
 * activated service account) before invoking this task.
 */
@DisableCachingByDefault(because = "Executes tests on remote devices; results must never be served from the build cache")
abstract class FirebaseTestLabTask
@Inject
constructor(
  private val execOperations: ExecOperations
) : DefaultTask() {
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val appApkDirectory: DirectoryProperty

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val testApkDirectory: DirectoryProperty

  @get:Input
  abstract val devices: ListProperty<String>

  @get:Input
  abstract val useOrchestrator: Property<Boolean>

  @get:Input
  abstract val environmentVariables: MapProperty<String, String>

  @get:Input
  abstract val testTimeout: Property<String>

  @get:Input
  abstract val numFlakyTestAttempts: Property<Int>

  @get:Input
  @get:Optional
  abstract val gcloudProject: Property<String>

  @get:Input
  @get:Optional
  abstract val resultsBucket: Property<String>

  @get:Input
  @get:Optional
  abstract val resultsDir: Property<String>

  @get:Input
  @get:Optional
  abstract val testTargets: Property<String>

  @get:Input
  abstract val gcloudExecutable: Property<String>

  @get:Input
  abstract val extraArgs: ListProperty<String>

  @TaskAction
  fun run() {
    val appApk = findApk(appApkDirectory.get().asFile, "app")
    val testApk = findApk(testApkDirectory.get().asFile, "instrumentation test")

    val arguments = mutableListOf(
      gcloudExecutable.get(),
      "firebase", "test", "android", "run",
      "--type", "instrumentation",
      "--app", appApk.absolutePath,
      "--test", testApk.absolutePath,
      "--timeout", testTimeout.get(),
      "--num-flaky-test-attempts", numFlakyTestAttempts.get().toString()
    )

    devices.get().forEach { device ->
      arguments += listOf("--device", device)
    }

    if (useOrchestrator.get()) {
      arguments += "--use-orchestrator"
    }

    val environment = environmentVariables.get()
    if (environment.isNotEmpty()) {
      arguments += "--environment-variables"
      arguments += environment.entries.joinToString(",") { "${it.key}=${it.value}" }
    }

    gcloudProject.orNull?.takeIf { it.isNotBlank() }?.let { arguments += listOf("--project", it) }
    resultsBucket.orNull?.takeIf { it.isNotBlank() }?.let { arguments += listOf("--results-bucket", it) }
    resultsDir.orNull?.takeIf { it.isNotBlank() }?.let { arguments += listOf("--results-dir", it) }
    testTargets.orNull?.takeIf { it.isNotBlank() }?.let { arguments += listOf("--test-targets", it) }
    arguments += extraArgs.get()

    logger.lifecycle("Running Firebase Test Lab:\n  ${arguments.joinToString(" ")}")
    execOperations.exec {
      commandLine(arguments)
    }
  }

  private fun findApk(directory: File, label: String): File {
    return directory.walkTopDown().firstOrNull { it.isFile && it.extension == "apk" }
      ?: throw GradleException("No $label APK found under ${directory.absolutePath}. Was the assemble task run?")
  }
}

abstract class RenameApkTask : DefaultTask() {
  @get:InputFiles
  abstract val apkFolder: DirectoryProperty

  @get:OutputDirectory
  abstract val outFolder: DirectoryProperty

  @get:Internal
  abstract val transformationRequest: Property<ArtifactTransformationRequest<RenameApkTask>>

  @TaskAction
  fun rename() {
    transformationRequest.get().submit(this) { artifact ->
      val originalFile = File(artifact.outputFile)
      val versionName = artifact.versionName?.substringBefore(" |")
      val newName = if (!versionName.isNullOrEmpty()) {
        originalFile.name.replace(".apk", "-$versionName.apk")
      } else {
        originalFile.name
      }
      val newFile = File(outFolder.get().asFile, newName)
      originalFile.copyTo(newFile, overwrite = true)
      newFile
    }
  }
}
