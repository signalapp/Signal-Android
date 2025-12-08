import java.io.FileInputStream
import java.util.Properties

plugins {
  id("signal-sample-app")
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlinx.serialization)
}

val keystoreProperties: Properties? = loadKeystoreProperties("keystore.debug.properties")

android {
  namespace = "org.signal.registration.sample"

  defaultConfig {
    // IMPORTANT: We use the same package name as the signal staging app so that FCM works.
    applicationId = "org.thoughtcrime.securesms.staging"
    versionCode = 1
    versionName = "1.0"

    minSdk = 26
    targetSdk = 34
  }

  buildFeatures {
    compose = true
  }

  keystoreProperties?.let { properties ->
    signingConfigs.getByName("debug").apply {
      storeFile = file("${project.rootDir}/${properties.getProperty("storeFile")}")
      storePassword = properties.getProperty("storePassword")
      keyAlias = properties.getProperty("keyAlias")
      keyPassword = properties.getProperty("keyPassword")
    }
  }

  buildTypes {
    getByName("debug") {
      if (keystoreProperties != null) {
        signingConfig = signingConfigs["debug"]
      }
    }
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
}

dependencies {
  lintChecks(project(":lintchecks"))

  // Registration library
  implementation(project(":registration"))

  // Core dependencies
  implementation(project(":core-ui"))
  implementation(project(":core-util"))
  implementation(project(":core-models"))
  implementation(project(":libsignal-service"))

  // libsignal-protocol for PreKeyCollection types
  implementation(libs.libsignal.client)

  // Kotlin serialization for JSON parsing
  implementation(libs.kotlinx.serialization.json)

  // AndroidX
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.sqlite)
  implementation(libs.androidx.sqlite.framework)

  // Lifecycle
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Kotlinx Serialization
  implementation(libs.kotlinx.serialization.json)

  // Navigation 3
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.navigation3.ui)

  // Compose BOM
  platform(libs.androidx.compose.bom).let { composeBom ->
    implementation(composeBom)
  }

  // Compose dependencies
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui.tooling.preview)
  debugImplementation(libs.androidx.compose.ui.tooling.core)

  // Firebase & Play Services
  implementation(libs.firebase.messaging) {
    exclude(group = "com.google.firebase", module = "firebase-core")
    exclude(group = "com.google.firebase", module = "firebase-analytics")
    exclude(group = "com.google.firebase", module = "firebase-measurement-connector")
  }
  implementation(libs.google.play.services.base)
  implementation(libs.kotlinx.coroutines.play.services)
}

fun loadKeystoreProperties(filename: String): Properties? {
  val keystorePropertiesFile = file("${project.rootDir}/$filename")

  return if (keystorePropertiesFile.exists()) {
    val properties = Properties()
    properties.load(FileInputStream(keystorePropertiesFile))
    properties
  } else {
    null
  }
}
