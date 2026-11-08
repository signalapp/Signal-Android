/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.signal.buildtools.PseudoLanguage

/**
 * Writes every [PseudoLanguage] translation of a module's English strings as a resource directory of its own.
 */
@CacheableTask
abstract class GeneratePseudoLocalesTask : DefaultTask() {
  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val englishStrings: RegularFileProperty

  @get:OutputDirectory
  abstract val outputDirectory: DirectoryProperty

  @TaskAction
  fun generate() {
    val english = englishStrings.get().asFile
    val output = outputDirectory.get().asFile
    output.deleteRecursively()

    for (language in PseudoLanguage.entries) {
      val directory = output.resolve(language.valuesDirectoryName).apply { mkdirs() }
      directory.resolve("strings.xml").writeText(language.translateStringsXml(english))
    }
  }
}
