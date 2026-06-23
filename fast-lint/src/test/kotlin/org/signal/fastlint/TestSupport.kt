package org.signal.fastlint

import org.signal.fastlint.rules.ALL_RULES
import java.io.File

/**
 * Shared engine for rule tests. Created once per test JVM (PSI environment setup is expensive) and
 * reused; tests run sequentially within a JVM so the single instance is safe.
 */
private val ENGINE = FastLint(ALL_RULES)

fun lintKotlin(code: String, fileName: String = "Test.kt"): List<Finding> =
  ENGINE.analyzeKotlin(File("/repo/app/src/main/java/org/test/$fileName"), code)

fun lintJava(code: String, fileName: String = "Test.java"): List<Finding> =
  ENGINE.analyzeJava(File("/repo/app/src/main/java/org/test/$fileName"), code)

fun lintLayout(xml: String): List<Finding> =
  ENGINE.analyzeXml(File("/repo/app/src/main/res/layout/test.xml"), xml)

fun lintValues(xml: String): List<Finding> =
  ENGINE.analyzeXml(File("/repo/app/src/main/res/values/strings.xml"), xml)

/** Sorted list of check ids in the findings, for concise assertions. */
fun List<Finding>.ids(): List<String> = map { it.checkId }.sorted()
