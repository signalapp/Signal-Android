/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

import Licenses.LicenseData
import groovy.xml.XmlSlurper
import groovy.xml.slurpersupport.GPathResult
import org.gradle.internal.logging.text.StyledTextOutput
import org.gradle.internal.logging.text.StyledTextOutputFactory
import org.gradle.kotlin.dsl.support.serviceOf
import org.xml.sax.helpers.DefaultHandler

val out = project.serviceOf<StyledTextOutputFactory>().create("output")

/**
 * Finds the licenses for all of our dependencies and saves them to app/src/main/res/raw/third_party_licenses.
 *
 * This task will fail if we cannot map an artifact's license to a known license in [Licenses]. If this happens,
 * you need to manually save the new license, or map the URL to an existing license in [Licenses.getLicense].
 */
task("saveLicenses") {
  description = "Finds the licenses for all of our dependencies and saves them to app/src/main/res/raw/third_party_licenses."
  group = "Static Files"

  doLast {
    // We resolve all the artifacts, map them to a dependency that lets us fetch the POM metadata, and then use that to generate our models
    val resolvedDependencies: List<ResolvedDependency> = configurations
      .asSequence()
      .filter { it.isCanBeResolved }
      .mapNotNull { it.tryResolveConfiguration() }
      .mapNotNull { it.tryToResolveArtifacts() }
      .flatten()
      .distinctBy { it.file }
      .map { pomDependency(it.id.componentIdentifier.toString()) }
      .mapNotNull { it.toResolvedDependency() }
      .filter { it.licenses.isNotEmpty() }
      .distinct()
      .toList()

    // Next we want to map each dependency to a known license, failing if we can't do so
    val licenseToDependencies: MutableMap<LicenseData, MutableList<ResolvedDependency>> = mutableMapOf()

    for (resolvedDependency in resolvedDependencies) {
      for (license in resolvedDependency.licenses) {
        val licenseData: LicenseData? = Licenses.getLicense(license.url)

        if (licenseData == null) {
          printlnError("Failed to find matching license data for ${license.name} (${license.url}), which is in use by $resolvedDependency")
          throw RuntimeException("Failed to find matching license data! See output for details.")
        }

        licenseToDependencies.getOrPut(licenseData) { mutableListOf() } += resolvedDependency
      }
    }

    // Now we can build the actual string that we'll write to the file
    val output = StringBuilder()

    licenseToDependencies
      .entries
      .sortedByDescending { it.value.size }
      .forEach { entry ->
        val license: LicenseData = entry.key

        // Some companies have multiple artifacts that are named identically (and licensed identically).
        // This just dedupes it based on the name+url so that you don't see the same name in the list twice.
        val dependencies: List<ResolvedDependency> = entry.value.distinctBy { it.name + it.url }.sortedBy { it.name }

        output.append("The following dependencies are licensed under ${license.name}:\n\n")
        for (dependency in dependencies) {
          output.append("* ${dependency.name}")
          if (dependency.url != null) {
            output.append(" (${dependency.url})")
          }
          output.append("\n")
        }

        output.append("\n")
        output.append("==========================================================\n")
        output.append("==========================================================\n")
        output.append(license.text)
        output.append("\n")
        output.append("==========================================================\n")
        output.append("==========================================================\n")
        output.append("\n")
      }

    output.append("Kyber Patent License\n")
    output.append("https://csrc.nist.gov/csrc/media/Projects/post-quantum-cryptography/documents/selected-algos-2022/nist-pqc-license-summary-and-excerpts.pdf\n")
    output.append("\n")

    // Save the file to disk
    rootProject
      .file("app/src/main/res/raw/third_party_licenses")
      .writeText(output.toString())

    printlnSuccess("Done!")
  }
}

/**
 * Converts a dependency for a POM to a [ResolvedDependency], which is just our nice and usable internal representation of all the data we need.
 *
 * @param leafDependency The actual dependency we're trying to resolve. If present, it means that [this] refers to a _parent_ of that dependency, which we're
 * looking at just to try to get licensing information.
 */
fun Dependency.toResolvedDependency(leafDependency: ResolvedDependency? = null): ResolvedDependency? {
  val pomConfiguration: Configuration = project.configurations.detachedConfiguration(this)
  val pomFile: File = try {
    pomConfiguration.resolve().first()
  } catch (e: Exception) {
    printlnWarning("[${this.id}] Failed to resolve the POM dependency to a file.")
    return null
  }

  val xmlParser = XmlSlurper(true, false).apply {
    errorHandler = DefaultHandler()
  }

  val xml: GPathResult = xmlParser.parse(pomFile)

  val licenses: List<RawLicense> = try {
    xml
      .get("licenses")
      ?.get("license")
      ?.map { it as GPathResult }
      ?.map {
        RawLicense(
          name = it.get("name")?.text()?.trim() ?: "",
          url = it.get("url")?.text()?.trim() ?: ""
        )
      }
      ?.filter {
        it.name.isNotEmpty() && it.url.isNotEmpty()
      } ?: emptyList()
  } catch (e: Exception) {
    printlnWarning("[${this.id}] Error when parsing XML")
    e.printStackTrace()
    emptyList()
  }

  // If we have a leafDependency, we just want to copy the possibly-found license into it, leaving the metadata alone, since that's the actual target of
  // our search
  val resolvedDependency = if (leafDependency != null) {
    leafDependency.copy(licenses = licenses)
  } else {
    ResolvedDependency(
      id = this.id,
      name = xml.get("name")?.text()?.ifBlank { null } ?: xml.get("artifactId")?.text()?.ifBlank { null } ?: this.name,
      url = xml.get("url")?.text()?.ifBlank { null },
      licenses = licenses
    )
  }

  // If there's no licenses, but a parent exists, then we can walk up the tree and try to find a license in a parent
  if (resolvedDependency.licenses.isEmpty()) {
    val parentGroup: String? = xml.get("parent")?.get("groupId")?.text()?.trim()
    val parentName: String? = xml.get("parent")?.get("artifactId")?.text()?.trim()
    val parentVersion: String? = xml.get("parent")?.get("version")?.text()?.trim()

    if (parentGroup != null && parentName != null && parentVersion != null) {
      printlnNormal("[${this.id}] Could not find a license on this node. Checking the parent.")
      return pomDependency("$parentGroup:$parentName:$parentVersion").toResolvedDependency(leafDependency = resolvedDependency)
    }
  } else if (leafDependency != null) {
    printlnNormal("[${leafDependency.id}] Found a license on a parent dependency. (parent = ${this.id})")
  }

  return resolvedDependency
}

fun printlnNormal(message: String) {
  out.style(StyledTextOutput.Style.Normal).println(message)
}

fun printlnWarning(message: String) {
  out.style(StyledTextOutput.Style.Description).println(message)
}

fun printlnSuccess(message: String) {
  out.style(StyledTextOutput.Style.SuccessHeader).println(message)
}

fun printlnError(message: String) {
  out.style(StyledTextOutput.Style.FailureHeader).println(message)
}

fun pomDependency(locator: String): Dependency {
  return project.dependencies.create("$locator@pom")
}

fun Configuration.tryResolveConfiguration(): ResolvedConfiguration? {
  return try {
    this.resolvedConfiguration
  } catch (e: Exception) {
    null
  }
}

fun ResolvedConfiguration.tryToResolveArtifacts(): Set<ResolvedArtifact>? {
  return try {
    this.resolvedArtifacts
  } catch (e: Exception) {
    null
  }
}

fun GPathResult.get(key: String): GPathResult? {
  return this.getProperty(key) as? GPathResult
}

val Dependency.id: String
  get() = "${this.group}:${this.name}:${this.version}"

data class ResolvedDependency(
  val id: String,
  val name: String,
  val url: String?,
  val licenses: List<RawLicense>
)

data class RawLicense(val name: String, val url: String)
