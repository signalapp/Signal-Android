@file:JvmName("Lint")

package org.signal.fastlint

import java.io.File
import kotlin.system.exitProcess

/**
 * A lightweight linter that does real AST traversal of Kotlin (PSI) and Java (PSI) plus streaming
 * traversal of XML resources, using the same parser front-ends that power lint/ktlint.
 *
 * Note that this lint is parse-only: there is no symbol resolution or classpath, so receiver classes
 * are resolved syntactically via the import table.
 *
 * Suppression honors @SuppressLint/@SuppressWarnings/@Suppress in code and tools:ignore in XML.
 *
 * Usage: Lint <repo-root> [--report=<file>]
 */
fun main(args: Array<String>) {
  val root = File(args.firstOrNull { !it.startsWith("--") } ?: ".").absoluteFile
  val reportPath = args.firstOrNull { it.startsWith("--report=") }?.substringAfter('=')

  val start = System.nanoTime()
  val result = FastLint().use { it.run(root) }
  val elapsedMs = (System.nanoTime() - start) / 1_000_000

  val sorted = result.findings.sortedWith(compareBy({ it.file.path }, { it.line }, { it.checkId }))
  val rootPrefix = root.path + "/"

  val sb = StringBuilder()
  sb.appendLine("fast-lint: scanned ${result.kotlinFiles} Kotlin + ${result.javaFiles} Java + ${result.xmlFiles} XML files in ${elapsedMs}ms")
  sb.appendLine()
  if (sorted.isEmpty()) {
    sb.appendLine("No issues found.")
  } else {
    sb.appendLine("Issues by check:")
    sorted.groupingBy { it.checkId }.eachCount().toSortedMap().forEach { (id, count) ->
      sb.appendLine("  %-34s %d".format(id, count))
    }
    sb.appendLine("  %-34s %d".format("TOTAL", sorted.size))
    sb.appendLine()
    sb.appendLine("Issues:")
    sorted.forEach {
      sb.appendLine("  ${it.file.path.removePrefix(rootPrefix)}:${it.line}: ${it.checkId}: ${it.message}")
    }
  }
  val output = sb.toString()
  print(output)

  if (reportPath != null) {
    File(reportPath).apply { parentFile?.mkdirs() }.writeText(output)
  }

  if (sorted.isNotEmpty()) {
    System.err.println("\nfast-lint found ${sorted.size} issue(s). Suppress legitimate cases with @SuppressLint(\"<CheckId>\") or tools:ignore.")
    exitProcess(1)
  }
  exitProcess(0)
}
