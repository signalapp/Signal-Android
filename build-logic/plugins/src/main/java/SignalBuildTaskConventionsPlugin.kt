/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.tasks.testing.Test
import org.gradle.external.javadoc.StandardJavadocDocletOptions

internal class SignalBuildTaskConventionsPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    if (JavaVersion.current().isJava8Compatible) {
      target.tasks.withType(Javadoc::class.java).configureEach {
        (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
      }
    }

    target.tasks.withType(Test::class.java).configureEach {
      maxParallelForks = (Runtime.getRuntime().availableProcessors() / 4).coerceAtLeast(1)
    }
  }
}
