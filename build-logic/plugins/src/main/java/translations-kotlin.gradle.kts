import org.signal.buildtools.SmartlingClient
import java.io.File
import java.util.Properties


/**
 * Kotlin-based translation tasks.
 *
 * Requires the following properties in local.properties:
 *   smartling.userIdentifier - Smartling API user identifier
 *   smartling.userSecret - Smartling API user secret
 *   smartling.projectId - (optional) Smartling project ID, defaults to "3e5533321"
 */

tasks.register("pushTranslations") {
  group = "Translations"
  description = "Pushes the main strings.xml file to Smartling for translation"

  doLast {
    val localPropertiesFile = File(rootDir, "local.properties")
    if (!localPropertiesFile.exists()) {
      throw GradleException("local.properties not found at ${localPropertiesFile.absolutePath}")
    }

    val localProperties = Properties().apply {
      localPropertiesFile.inputStream().use { load(it) }
    }

    val userIdentifier = localProperties.requireProperty("smartling.userIdentifier")
    val userSecret = localProperties.requireProperty("smartling.userSecret")
    val projectId = localProperties.requireProperty("smartling.projectId")

    val stringsFile = File(rootDir, "app/src/main/res/values/strings.xml")
    if (!stringsFile.exists()) {
      throw GradleException("strings.xml not found at ${stringsFile.absolutePath}")
    }

    println("Using Signal-Android root directory of $rootDir")

    val client = SmartlingClient(userIdentifier, userSecret, projectId)

    println("Fetching auth...")
    val authToken = client.authenticate()
    println("> Done")
    println()

    println("Uploading file...")
    val response = client.uploadFile(authToken, stringsFile, "strings.xml")
    println(response)
    println("> Done")
  }
}

private fun Properties.requireProperty(name: String): String {
  return getProperty(name) ?: throw GradleException("$name not found in local.properties")
}
