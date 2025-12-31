import groovy.util.Node
import groovy.xml.XmlParser
import org.signal.buildtools.StaticIpResolver
import java.io.File

/**
 * Tasks for managing translations and static files.
 */

tasks.register("replaceEllipsis") {
  group = "Static Files"
  description = "Process strings for ellipsis characters."
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

tasks.register("postTranslateQa") {
  group = "Static Files"
  description = "Runs QA to check validity of updated strings, and ensure presence of any new languages in internal lists."
  dependsOn(":qa")
}

tasks.register("resolveStaticIps") {
  group = "Static Files"
  description = "Fetches static IPs for core hosts and writes them to static-ips.gradle"
  doLast {
    val staticIpResolver = StaticIpResolver()
    val content = """
      rootProject.extra["service_ips"] = ${"\"\"\""}${staticIpResolver.resolveToBuildConfig("chat.signal.org")}${"\"\"\""}
      rootProject.extra["storage_ips"] = ${"\"\"\""}${staticIpResolver.resolveToBuildConfig("storage.signal.org")}${"\"\"\""}
      rootProject.extra["cdn_ips"] = ${"\"\"\""}${staticIpResolver.resolveToBuildConfig("cdn.signal.org")}${"\"\"\""}
      rootProject.extra["cdn2_ips"] = ${"\"\"\""}${staticIpResolver.resolveToBuildConfig("cdn2.signal.org")}${"\"\"\""}
      rootProject.extra["cdn3_ips"] = ${"\"\"\""}${staticIpResolver.resolveToBuildConfig("cdn3.signal.org")}${"\"\"\""}
      rootProject.extra["sfu_ips"] = ${"\"\"\""}${staticIpResolver.resolveToBuildConfig("sfu.voip.signal.org")}${"\"\"\""}
      rootProject.extra["content_proxy_ips"] = ${"\"\"\""}${staticIpResolver.resolveToBuildConfig("contentproxy.signal.org")}${"\"\"\""}
      rootProject.extra["svr2_ips"] = ${"\"\"\""}${staticIpResolver.resolveToBuildConfig("svr2.signal.org")}${"\"\"\""}
      rootProject.extra["cdsi_ips"] = ${"\"\"\""}${staticIpResolver.resolveToBuildConfig("cdsi.signal.org")}${"\"\"\""}
    """.trimIndent() + "\n"
    File(projectDir, "static-ips.gradle.kts").writeText(content)
  }
}

tasks.register("updateStaticFilesAndQa") {
  group = "Static Files"
  description = "Runs tasks to update static files. This includes translations, static IPs, and licenses. Runs QA afterwards to verify all went well. Intended to be run before cutting a release."
  dependsOn("replaceEllipsis", "cleanApostropheErrors", "excludeNonTranslatables", "resolveStaticIps", "postTranslateQa")
}

private fun allStringsResourceFiles(action: (File) -> Unit) {
  val resDir = file("src/main/res")
  resDir.walkTopDown()
    .filter { it.isFile && it.name == "strings.xml" }
    .forEach(action)
}
