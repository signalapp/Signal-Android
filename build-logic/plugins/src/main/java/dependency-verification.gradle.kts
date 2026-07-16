/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

run {
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

  // Run when you need to update verification-metadata.
  // Has to be a little funny and call out to the shell so that we can pass in the proper args.
  val isWindows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
  val wrapper = if (isWindows) listOf("cmd", "/c", "gradlew.bat") else listOf("./gradlew")

  tasks.register<UpdateVerificationMetadataTask>("updateVerificationMetadata") {
    group = "Verification"
    description = "Rebuilds gradle/verification-metadata.xml with checksums for all current dependencies on the host platform and every other OS variant (linux/osx/windows)."
    metadataFile.set(rootProject.layout.projectDirectory.file("gradle/verification-metadata.xml"))
    rootDirectory.set(rootProject.layout.projectDirectory)
    wrapperCommand.set(wrapper)
  }
}
