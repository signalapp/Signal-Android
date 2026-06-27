/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jlleitschuh.gradle.ktlint.KtlintExtension

internal class KtlintPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    target.pluginManager.apply("org.jlleitschuh.gradle.ktlint")

    target.extensions.configure<KtlintExtension> {
      version.set("1.5.0")
    }
  }
}
