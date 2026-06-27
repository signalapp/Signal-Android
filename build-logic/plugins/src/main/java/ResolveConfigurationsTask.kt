/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Resolves a set of dependency artifacts without producing any build output. The artifacts are wired in at
 * configuration time as a file collection, so the task action only has to realize them, which downloads
 * anything missing. Used by the dependency-verification plugin to populate dependency verification metadata.
 */
internal abstract class ResolveConfigurationsTask : DefaultTask() {
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val artifactFiles: ConfigurableFileCollection

  @TaskAction
  fun resolve() {
    logger.lifecycle("Resolved ${artifactFiles.files.size} external artifact(s) for dependency verification.")
  }
}
