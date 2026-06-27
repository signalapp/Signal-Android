/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.tasks.Exec
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

internal class DependencyVerificationPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    with(target) {
      val syncTaskName = "syncCrossPlatformVerification"
      val platforms = listOf("linux", "osx", "windows")

      val syncTask = tasks.register(syncTaskName) {
        group = "Verification"
        description = "Resolves every OS variant ($platforms) of platform-specific dependencies so their checksums can be written to verification-metadata.xml regardless of host OS."
      }

      // Only parse the (large) metadata file when this task is actually requested, to avoid slowing normal builds.
      if (gradle.startParameter.taskNames.any { it.substringAfterLast(':') == syncTaskName }) {
        val metadataFile = rootProject.file("gradle/verification-metadata.xml")
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(metadataFile)
        val components = doc.getElementsByTagName("component")
        val resolvedFiles = files()

        for (i in 0 until components.length) {
          val component = components.item(i) as Element
          val group = component.getAttribute("group")
          val name = component.getAttribute("name")
          val version = component.getAttribute("version")
          val prefix = "$name-$version-"

          val extensions = mutableSetOf<String>()
          val artifacts = component.getElementsByTagName("artifact")
          for (j in 0 until artifacts.length) {
            val artifactName = (artifacts.item(j) as Element).getAttribute("name")
            if (!artifactName.startsWith(prefix)) {
              continue
            }

            val rest = artifactName.removePrefix(prefix)
            val dot = rest.lastIndexOf('.')
            if (dot <= 0) {
              continue
            }

            if (rest.substring(0, dot) in platforms) {
              extensions += rest.substring(dot + 1)
            }
          }
          if (extensions.isEmpty()) continue

          // A configuration per component+version: putting multiple versions of the same module in one
          // configuration would trigger version-conflict resolution and collapse them to the newest one.
          val configuration = configurations.create("crossPlatformVerification_${group.replace('.', '_')}_${name}_${version.replace(Regex("[^A-Za-z0-9]"), "_")}") {
            isCanBeConsumed = false
            isCanBeResolved = true
            isTransitive = false
          }
          for (extension in extensions) {
            for (platform in platforms) {
              dependencies.add(configuration.name, "$group:$name:$version:$platform@$extension")
            }
          }
          resolvedFiles.from(configuration.incoming.files)
        }

        syncTask.configure {
          inputs.files(resolvedFiles)
          doLast {
            println("Resolved ${resolvedFiles.files.size} cross-platform artifact(s) for verification.")
          }
        }
      }

      // Builds tasks to get task dependencies without actually running them.
      val resolveForVerification = tasks.register("resolveDependenciesForVerification") {
        group = "Verification"
        description = "Resolves all external dependencies on every resolvable configuration (including test/flavor variants) without building anything, so their checksums can be written to verification-metadata.xml."
      }

      allprojects {
        // Only buildable modules (those applying the base plugin) have resolvable configurations and a clean
        // task; skipping the rest avoids empty container projects like :core that have nothing to resolve.
        plugins.withType<BasePlugin> {
          val perProjectResolve = tasks.register<ResolveConfigurationsTask>("resolveConfigurationsForVerification") {
            group = "Verification"
            description = "Resolves the external dependencies of every resolvable configuration in $path without building."

            val task = this
            configurations.matching { it.isCanBeResolved }.all {
              task.artifactFiles.from(
                incoming.artifactView {
                  isLenient = true
                  componentFilter { it !is ProjectComponentIdentifier }
                }.files
              )
            }
          }
          resolveForVerification.configure { dependsOn(perProjectResolve) }
        }
      }

      // Run when you need to update verification-metadata.
      // Has to be a little funny and call out to the shell so that we can pass in the proper args.
      val isWindows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
      val wrapper = if (isWindows) listOf("cmd", "/c", "gradlew.bat") else listOf("./gradlew")

      // Normal resolution
      val writeHost = tasks.register<Exec>("writeHostVerificationMetadata") {
        group = "Verification"
        description = "Pass 1 of updateVerificationMetadata: resolves and writes checksums for dependencies on the host OS, without building."
        workingDir = rootDir
        commandLine = wrapper + listOf("--write-verification-metadata", "sha256", "resolveDependenciesForVerification", "--rerun-tasks")
      }

      // A follow-up tasks that detects platform-specific dependencies and fetches those you don't have (i.e. -linux, -osx, -windows variants)
      val writeCrossPlatform = tasks.register<Exec>("writeCrossPlatformVerificationMetadata") {
        group = "Verification"
        description = "Pass 2 of updateVerificationMetadata: fills in the other OS variants (linux/osx/windows) of platform-specific dependencies."
        workingDir = rootDir
        commandLine = wrapper + listOf("--write-verification-metadata", "sha256", "syncCrossPlatformVerification", "--rerun-tasks")
        mustRunAfter(writeHost)
      }

      // The actual task that executes all of the above
      tasks.register("updateVerificationMetadata") {
        group = "Verification"
        description = "Updates gradle/verification-metadata.xml with checksums for the host platform and for every other OS variant (linux/osx/windows). Run this after adding or updating a dependency."
        dependsOn(writeHost, writeCrossPlatform)
      }
    }
  }
}
