import groovy.util.Node
import groovy.xml.XmlParser
import org.signal.buildtools.SmartlingClient
import org.signal.buildtools.StaticIpResolver
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
// Module Discovery
// =====================

/**
 * Represents a module containing translatable string resources.
 *
 * @property name Human-readable module name (e.g., "app", "lib-device-transfer")
 * @property fileUri Smartling file identifier. Uses "strings.xml" for app (backward compat), otherwise "{name}-strings.xml"
 * @property stringsFile Path to the source English strings.xml
 * @property resDir Path to the module's res directory for writing translated files
 */
data class TranslatableModule(
  val name: String,
  val fileUri: String,
  val stringsFile: File,
  val resDir: File
)

/**
 * Discovers all modules with translatable strings by scanning for `strings.xml` files
 * in `src/main/res/values/` directories. Excludes demo apps.
 */
private fun discoverTranslatableModules(): List<TranslatableModule> {
  return rootDir.walkTopDown()
    .filter { it.name == "strings.xml" && it.parentFile.name == "values" }
    .filter { it.path.contains("src${File.separator}main${File.separator}res") }
    .filter { !it.path.contains("${File.separator}demo${File.separator}") }
    .map { stringsFile ->
      val resDir = stringsFile.parentFile.parentFile
      val modulePath = resDir.parentFile.parentFile.parentFile
      val moduleName = modulePath.relativeTo(rootDir).path
        .replace(File.separator, "-")
        .ifEmpty { "app" }
      val fileUri = if (moduleName == "app") "strings.xml" else "$moduleName-strings.xml"
      TranslatableModule(moduleName, fileUri, stringsFile, resDir)
    }
    .sortedBy { it.name }
    .toList()
}

/**
 * Information about translatable strings in a strings.xml file.
 */
data class StringsInfo(
  val totalCount: Int,
  val translatableCount: Int,
  val hasTranslatable: Boolean
)

/**
 * Analyzes a strings.xml file to count total and translatable strings.
 * Parses the file once and returns counts for both all strings and translatable strings.
 * Only counts actual string resources: <string>, <plurals>, and <string-array> elements.
 * Excludes placeholder <item type="string" /> declarations.
 */
private fun analyzeStrings(stringsFile: File): StringsInfo {
  return try {
    val xml = XmlParser().parse(stringsFile)
    val stringNodes = xml.children()
      .filterIsInstance<Node>()
      .filter { node ->
        val nodeName = node.name().toString()
        nodeName == "string" || nodeName == "plurals" || nodeName == "string-array"
      }

    val totalCount = stringNodes.size
    val translatableCount = stringNodes.count { node ->
      node.attribute("translatable") != "false"
    }

    StringsInfo(
      totalCount = totalCount,
      translatableCount = translatableCount,
      hasTranslatable = translatableCount > 0
    )
  } catch (e: Exception) {
    // If we can't parse the file, return -1 to indicate error
    StringsInfo(totalCount = -1, translatableCount = -1, hasTranslatable = true)
  }
}

// =====================
// Smartling Tasks
// =====================

tasks.register("translationsDryRun") {
  group = "Translations"
  description = "Preview discovered modules and translation files without making API calls"
  notCompatibleWithConfigurationCache("Uses script-level functions that capture Gradle objects")

  doLast {
    val modules = discoverTranslatableModules()

    logger.lifecycle("")
    logger.lifecycle("=".repeat(60))
    logger.lifecycle("Translations Dry Run - Module Discovery")
    logger.lifecycle("=".repeat(60))
    logger.lifecycle("")
    logger.lifecycle("Discovered ${modules.size} translatable module(s):")
    logger.lifecycle("")

    modules.forEach { module ->
      val info = analyzeStrings(module.stringsFile)
      logger.lifecycle("  Module: ${module.name}")
      logger.lifecycle("    File URI:     ${module.fileUri}")
      logger.lifecycle("    Source file:  ${module.stringsFile.relativeTo(rootDir)}")
      logger.lifecycle("    Resource dir: ${module.resDir.relativeTo(rootDir)}")
      logger.lifecycle("    String count: ${info.translatableCount} translatable, ${info.totalCount} total")
      logger.lifecycle("    Will upload:  ${if (info.hasTranslatable) "Yes" else "No (no translatable strings)"}")
      logger.lifecycle("")
    }

    logger.lifecycle("=".repeat(60))
    logger.lifecycle("Push would upload ${modules.size} file(s) to Smartling")
    logger.lifecycle("Pull would download translations to ${modules.size} module(s)")
    logger.lifecycle("=".repeat(60))
  }
}

tasks.register("pushTranslations") {
  group = "Translations"
  description = "Pushes strings.xml files from all modules to Smartling for translation. Use -PdryRun to preview."
  notCompatibleWithConfigurationCache("Uses script-level functions that capture Gradle objects")

  doLast {
    val dryRun = project.hasProperty("dryRun")
    val modules = discoverTranslatableModules()

    if (modules.isEmpty()) {
      throw GradleException("No translatable modules found")
    }

    logger.lifecycle("Using Signal-Android root directory of $rootDir")
    logger.lifecycle("Found ${modules.size} module(s) to push")
    if (dryRun) {
      logger.lifecycle("")
      logger.lifecycle("[DRY-RUN MODE - No files will be uploaded]")
    }
    logger.lifecycle("")

    val client = if (dryRun) null else createSmartlingClient()
    val authToken = if (dryRun) {
      null
    } else {
      logger.lifecycle("Fetching auth...")
      val token = client!!.authenticate()
      logger.lifecycle("> Done")
      logger.lifecycle("")
      token
    }

    var skippedCount = 0
    for (module in modules) {
      if (!module.stringsFile.exists()) {
        logger.warn("strings.xml not found for module ${module.name} at ${module.stringsFile.absolutePath}")
        continue
      }

      val info = analyzeStrings(module.stringsFile)

      // Skip files with no translatable strings
      if (!info.hasTranslatable) {
        logger.lifecycle("Skipping ${module.name}: No translatable strings found (${info.totalCount} non-translatable)")
        skippedCount++
        continue
      }

      if (dryRun) {
        logger.lifecycle("[DRY-RUN] Would upload: ${module.stringsFile.relativeTo(rootDir)}")
        logger.lifecycle("          File URI: ${module.fileUri}")
        logger.lifecycle("          Strings:  ${info.translatableCount} translatable")
        logger.lifecycle("")
      } else {
        logger.lifecycle("Uploading ${module.fileUri} (${info.translatableCount} translatable strings)...")
        val response = client!!.uploadFile(authToken!!, module.stringsFile, module.fileUri)
        logger.lifecycle(response)
        logger.lifecycle("> Done")
        logger.lifecycle("")
      }
    }

    if (dryRun) {
      logger.lifecycle("=".repeat(60))
      val uploadCount = modules.size - skippedCount
      logger.lifecycle("[DRY-RUN] Would have uploaded $uploadCount file(s)")
      if (skippedCount > 0) {
        logger.lifecycle("          Skipped $skippedCount file(s) with no translatable strings")
      }
      logger.lifecycle("Run without -PdryRun to actually upload")
      logger.lifecycle("=".repeat(60))
    } else {
      if (skippedCount > 0) {
        logger.lifecycle("")
        logger.lifecycle("Skipped $skippedCount file(s) with no translatable strings")
      }
    }
  }
}

tasks.register("pullTranslations") {
  group = "Translations"
  description = "Pulls translated strings.xml files from Smartling for all modules and locales. Use -PdryRun to preview."
  mustRunAfter("pushTranslations")
  notCompatibleWithConfigurationCache("Uses script-level functions that capture Gradle objects")

  doLast {
    val dryRun = project.hasProperty("dryRun")
    val modules = discoverTranslatableModules()

    if (modules.isEmpty()) {
      throw GradleException("No translatable modules found")
    }

    logger.lifecycle("Using Signal-Android root directory of $rootDir")
    logger.lifecycle("Found ${modules.size} module(s) to pull translations for")
    if (dryRun) {
      logger.lifecycle("")
      logger.lifecycle("[DRY-RUN MODE - No files will be downloaded or written]")
    }
    logger.lifecycle("")

    val client = createSmartlingClient()

    logger.lifecycle("Fetching auth...")
    val authToken = client.authenticate()
    logger.lifecycle("> Done")
    logger.lifecycle("")

    for (module in modules) {
      logger.lifecycle("Processing module: ${module.name}")
      logger.lifecycle("  File URI: ${module.fileUri}")

      logger.lifecycle("  Fetching locales...")
      val locales = try {
        client.getLocales(authToken, module.fileUri)
      } catch (e: Exception) {
        logger.warn("  Could not get locales for ${module.fileUri}: ${e.message}")
        logger.lifecycle("  (This may be normal for new modules that haven't been pushed yet)")
        logger.lifecycle("")
        continue
      }

      val filteredLocales = locales.filter { it !in localeBlocklist }
      logger.lifecycle("  Found ${locales.size} locales (${filteredLocales.size} after filtering)")
      logger.lifecycle("")

      if (dryRun) {
        logger.lifecycle("  [DRY-RUN] Would download ${filteredLocales.size} translations to:")
        logger.lifecycle("            ${module.resDir.relativeTo(rootDir)}/values-{locale}/strings.xml")
        logger.lifecycle("")
        continue
      }

      logger.lifecycle("  Fetching files...")
      val executor = Executors.newFixedThreadPool(35)
      val futures = mutableListOf<Future<Pair<String, String>>>()

      for (locale in filteredLocales) {
        futures += executor.submit<Pair<String, String>> {
          val content = client.downloadFile(authToken, module.fileUri, locale)
          logger.lifecycle("  Successfully pulled ${module.name} for locale $locale")
          locale to content
        }
      }

      val results = futures.map { it.get() }
      executor.shutdown()
      logger.lifecycle("  > Done fetching")

      logger.lifecycle("  Writing files...")
      for ((locale, content) in results) {
        val androidLocale = localeMap[locale] ?: locale
        val localeDir = File(module.resDir, "values-$androidLocale")
        localeDir.mkdirs()
        File(localeDir, "strings.xml").writeText(content)
      }
      logger.lifecycle("  > Done writing ${results.size} files")
      logger.lifecycle("")
    }

    if (dryRun) {
      logger.lifecycle("=".repeat(60))
      logger.lifecycle("[DRY-RUN] Would have downloaded translations for ${modules.size} module(s)")
      logger.lifecycle("Run without -PdryRun to actually download")
      logger.lifecycle("=".repeat(60))
    }
  }
}

tasks.register("replaceEllipsis") {
  group = "Static Files"
  description = "Process strings for ellipsis characters."
  mustRunAfter("pullTranslations")
  notCompatibleWithConfigurationCache("Uses script-level functions that capture Gradle objects")
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
  notCompatibleWithConfigurationCache("Uses script-level functions that capture Gradle objects")
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
  notCompatibleWithConfigurationCache("Uses script-level functions that capture Gradle objects")
  doLast {
    val modules = discoverTranslatableModules()

    for (module in modules) {
      val englishFile = module.stringsFile

      if (!englishFile.exists()) {
        logger.warn("English file not found for module ${module.name}, skipping excludeNonTranslatables")
        continue
      }

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

      module.resDir.walkTopDown()
        .filter { it.isFile && it.name == "strings.xml" && it != englishFile }
        .forEach { f ->
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
  notCompatibleWithConfigurationCache("Uses script-level functions that capture Gradle objects")
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

/**
 * Iterates over all strings.xml files in all translatable modules.
 * This includes the source English file and all translated locale files.
 */
private fun allStringsResourceFiles(action: (File) -> Unit) {
  val modules = discoverTranslatableModules()
  for (module in modules) {
    module.resDir.walkTopDown()
      .filter { it.isFile && it.name == "strings.xml" }
      .forEach(action)
  }
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
private val localeBlocklist = emptySet<String>()
