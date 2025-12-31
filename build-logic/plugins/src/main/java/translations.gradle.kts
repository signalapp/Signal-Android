import groovy.util.Node
import groovy.xml.XmlParser
import org.signal.buildtools.SmartlingClient
import org.signal.buildtools.StaticIpResolver
import java.io.File
import java.util.Properties
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Tasks for managing translations and static files.
 *
 * Smartling tasks require the following properties in local.properties:
 *   smartling.userIdentifier - Smartling API user identifier
 *   smartling.userSecret - Smartling API user secret
 *   smartling.projectId - Smartling project ID
 */

// =====================
// Smartling Tasks
// =====================

tasks.register("pushTranslations") {
  group = "Translations"
  description = "Pushes the main strings.xml file to Smartling for translation"

  doLast {
    val client = createSmartlingClient()

    val stringsFile = File(rootDir, "app/src/main/res/values/strings.xml")
    if (!stringsFile.exists()) {
      throw GradleException("strings.xml not found at ${stringsFile.absolutePath}")
    }

    println("Using Signal-Android root directory of $rootDir")

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

tasks.register("pullTranslations") {
  group = "Translations"
  description = "Pulls translated strings.xml files from Smartling for all locales"
  mustRunAfter("pushTranslations")

  doLast {
    val client = createSmartlingClient()
    val resDir = File(rootDir, "app/src/main/res")

    println("Using Signal-Android root directory of $rootDir")

    println("Fetching auth...")
    val authToken = client.authenticate()
    println("> Done")
    println()

    println("Fetching locales...")
    val locales = client.getLocales(authToken, "strings.xml")
    println("Found ${locales.size} locales")
    println("> Done")
    println()

    println("Fetching files...")
    val executor = Executors.newFixedThreadPool(35)
    val futures = mutableListOf<Future<Pair<String, String>>>()

    for (locale in locales) {
      if (locale in localeBlocklist) {
        continue
      }

      futures += executor.submit<Pair<String, String>> {
        val content = client.downloadFile(authToken, "strings.xml", locale)
        println("Successfully pulled file for locale $locale")
        locale to content
      }
    }

    val results = futures.map { it.get() }
    executor.shutdown()
    println("> Done")
    println()

    println("Writing files...")
    for ((locale, content) in results) {
      val androidLocale = localeMap[locale] ?: locale
      val localeDir = File(resDir, "values-$androidLocale")
      localeDir.mkdirs()
      File(localeDir, "strings.xml").writeText(content)
    }
    println("> Done")
  }
}

tasks.register("replaceEllipsis") {
  group = "Static Files"
  description = "Process strings for ellipsis characters."
  mustRunAfter("pullTranslations")
  doLast {
    allStringsResourceFiles { f ->
      val before = f.readText()
      val after = before.replace("...", "…")
      if (before != after) {
        f.writeText(after)
        logger.info("${f.parentFile.name}/${f.name}...updated")
      }
    }
  }
}

tasks.register("cleanApostropheErrors") {
  group = "Static Files"
  description = "Fix smartling apostrophe string errors."
  mustRunAfter("pullTranslations")
  doLast {
    val pattern = Regex("""([^\\=08])(')""")
    allStringsResourceFiles { f ->
      val before = f.readText()
      val after = pattern.replace(before) { match ->
        "${match.groupValues[1]}\\'"
      }
      if (before != after) {
        f.writeText(after)
        logger.info("${f.parentFile.name}/${f.name}...updated")
      }
    }
  }
}

tasks.register("excludeNonTranslatables") {
  group = "Static Files"
  description = "Remove strings that are marked \"translatable\"=\"false\" or are ExtraTranslations."
  mustRunAfter("pullTranslations")
  doLast {
    val englishFile = file("src/main/res/values/strings.xml")

    val english = XmlParser().parse(englishFile)
    val nonTranslatable = english.children()
      .filterIsInstance<Node>()
      .filter { it.attribute("translatable") == "false" }
      .mapNotNull { it.attribute("name") as? String }
      .toSet()
    val all = english.children()
      .filterIsInstance<Node>()
      .mapNotNull { it.attribute("name") as? String }
      .toSet()
    val translatable = all - nonTranslatable

    allStringsResourceFiles { f ->
      if (f != englishFile) {
        var inMultiline = false
        var endBlockName = ""

        val newLines = f.readLines().map { line ->
          if (!inMultiline) {
            val singleLineMatcher = Regex("""name="([^"]*)".*(<\/|\/>)""").find(line)
            if (singleLineMatcher != null) {
              val name = singleLineMatcher.groupValues[1]
              if (!line.contains("excludeNonTranslatables") && name !in translatable) {
                return@map "  <!-- Removed by excludeNonTranslatables ${line.trim()} -->"
              }
            } else {
              val multilineStartMatcher = Regex("""<(.*) .?name="([^"]*)".*""").find(line)
              if (multilineStartMatcher != null) {
                endBlockName = multilineStartMatcher.groupValues[1]
                val name = multilineStartMatcher.groupValues[2]
                if (!line.contains("excludeNonTranslatables") && name !in translatable) {
                  inMultiline = true
                  return@map "  <!-- Removed by excludeNonTranslatables ${line.trim()}"
                }
              }
            }
          } else {
            val multilineEndMatcher = Regex("""</$endBlockName""").find(line)
            if (multilineEndMatcher != null) {
              inMultiline = false
              return@map "$line -->"
            }
          }

          line
        }

        f.writeText(newLines.joinToString("\n") + "\n")
      }
    }
  }
}

tasks.register("resolveStaticIps") {
  group = "Static Files"
  description = "Fetches static IPs for core hosts and writes them to static-ips.gradle"
  doLast {
    val staticIpResolver = StaticIpResolver()
    val tripleQuote = "\"\"\""
    val content = """
      rootProject.extra["service_ips"] = $tripleQuote${staticIpResolver.resolveToBuildConfig("chat.signal.org")}$tripleQuote
      rootProject.extra["storage_ips"] = $tripleQuote${staticIpResolver.resolveToBuildConfig("storage.signal.org")}$tripleQuote
      rootProject.extra["cdn_ips"] = $tripleQuote${staticIpResolver.resolveToBuildConfig("cdn.signal.org")}$tripleQuote
      rootProject.extra["cdn2_ips"] = $tripleQuote${staticIpResolver.resolveToBuildConfig("cdn2.signal.org")}$tripleQuote
      rootProject.extra["cdn3_ips"] = $tripleQuote${staticIpResolver.resolveToBuildConfig("cdn3.signal.org")}$tripleQuote
      rootProject.extra["sfu_ips"] = $tripleQuote${staticIpResolver.resolveToBuildConfig("sfu.voip.signal.org")}$tripleQuote
      rootProject.extra["content_proxy_ips"] = $tripleQuote${staticIpResolver.resolveToBuildConfig("contentproxy.signal.org")}$tripleQuote
      rootProject.extra["svr2_ips"] = $tripleQuote${staticIpResolver.resolveToBuildConfig("svr2.signal.org")}$tripleQuote
      rootProject.extra["cdsi_ips"] = $tripleQuote${staticIpResolver.resolveToBuildConfig("cdsi.signal.org")}$tripleQuote
    """.trimIndent() + "\n"
    File(projectDir, "static-ips.gradle.kts").writeText(content)
  }
}

tasks.register("updateStaticFilesAndQa") {
  group = "Static Files"
  description = "Runs tasks to update static files. This includes translations, static IPs, and licenses. Runs QA afterwards to verify all went well. Intended to be run before cutting a release."
  dependsOn("pushTranslations", "pullTranslations", "replaceEllipsis", "cleanApostropheErrors", "excludeNonTranslatables", "resolveStaticIps", "postTranslateQa")
}

// This is a wrapper task just so that we can add a mustRunAfter in the context of the translation tasks.
tasks.register("postTranslateQa") {
  group = "Static Files"
  description = "Runs QA to check validity of updated strings, and ensure presence of any new languages in internal lists."
  mustRunAfter("replaceEllipsis", "cleanApostropheErrors", "excludeNonTranslatables", "resolveStaticIps")
  dependsOn(":qa")
}

private fun allStringsResourceFiles(action: (File) -> Unit) {
  val resDir = file("src/main/res")
  resDir.walkTopDown()
    .filter { it.isFile && it.name == "strings.xml" }
    .forEach(action)
}

private fun createSmartlingClient(): SmartlingClient {
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

  return SmartlingClient(userIdentifier, userSecret, projectId)
}

private fun Properties.requireProperty(name: String): String {
  return getProperty(name) ?: throw GradleException("$name not found in local.properties")
}

/**
 * A mapping of smartling-locale => Android locale.
 * Only needed when they differ.
 */
private val localeMap = mapOf(
  "af-ZA" to "af",
  "az-AZ" to "az",
  "be-BY" to "be",
  "bg-BG" to "bg",
  "bn-BD" to "bn",
  "bs-BA" to "bs",
  "et-EE" to "et",
  "fa-IR" to "fa",
  "ga-IE" to "ga",
  "gl-ES" to "gl",
  "gu-IN" to "gu",
  "he" to "iw",
  "hi-IN" to "hi",
  "hr-HR" to "hr",
  "id" to "in",
  "ka-GE" to "ka",
  "kk-KZ" to "kk",
  "km-KH" to "km",
  "kn-IN" to "kn",
  "ky-KG" to "ky",
  "lt-LT" to "lt",
  "lv-LV" to "lv",
  "mk-MK" to "mk",
  "ml-IN" to "ml",
  "mr-IN" to "mr",
  "pa-IN" to "pa",
  "pt-BR" to "pt-rBR",
  "pt-PT" to "pt",
  "ro-RO" to "ro",
  "sk-SK" to "sk",
  "sl-SI" to "sl",
  "sq-AL" to "sq",
  "sr-RS" to "sr-rRS",
  "sr-YR" to "sr",
  "ta-IN" to "ta",
  "te-IN" to "te",
  "tl-PH" to "tl",
  "uk-UA" to "uk",
  "zh-CN" to "zh-rCN",
  "zh-HK" to "zh-rHK",
  "zh-TW" to "zh-rTW",
  "zh-YU" to "yue"
)

/**
 * Locales that should not be saved, even if present remotely.
 * Typically for unfinished translations not ready to be public.
 */
val localeBlocklist = emptySet<String>()
