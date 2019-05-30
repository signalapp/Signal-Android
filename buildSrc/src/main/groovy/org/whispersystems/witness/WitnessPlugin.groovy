package org.whispersystems.witness

import org.gradle.api.InvalidUserDataException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedArtifact

import java.security.MessageDigest

class WitnessPluginExtension {
    List verify
    String configuration
}

class WitnessPlugin implements Plugin<Project> {

    static String calculateSha256(file) {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        file.eachByte 4096, {bytes, size ->
            md.update(bytes, 0, size);
        }
        return md.digest().collect {String.format "%02x", it}.join();
    }

    void apply(Project project) {
        project.extensions.create("dependencyVerification", WitnessPluginExtension)
        project.afterEvaluate {
            project.dependencyVerification.verify.each {
                assertion ->
                    List  parts  = assertion.tokenize(":")
                    String group = parts.get(0)
                    String name  = parts.get(1)
                    String hash  = parts.get(2)

                    def artifacts = allArtifacts(project).findAll {
                        return it.name.equals(name) && it.moduleVersion.id.group.equals(group)
                    }

                    if (artifacts.size() > 1) {
                        throw new InvalidUserDataException("Multiple artifacts found for $group:$name, ${artifacts.size()} found")
                    }

                    ResolvedArtifact dependency = artifacts.find()

                    println "Verifying " + group + ":" + name

                    if (dependency == null) {
                        throw new InvalidUserDataException("No dependency for integrity assertion found: " + group + ":" + name)
                    }

                    if (!hash.equals(calculateSha256(dependency.file))) {
                        throw new InvalidUserDataException("Checksum failed for " + assertion)
                    }
            }
        }

        project.task('calculateChecksums').doLast {
            println "dependencyVerification {"

            def configurationName = project.dependencyVerification.configuration
            if (configurationName != null) {
                println "    configuration = '$configurationName'"
            }

            println "    verify = ["

            allArtifacts(project).each {
                dep ->
                    println "        '" + dep.moduleVersion.id.group+ ":" + dep.name + ":" + calculateSha256(dep.file) + "',"
            }

            println "    ]"
            println "}"
        }
    }

    private static Set<ResolvedArtifact> allArtifacts(Project project) {
        def configurationName = project.dependencyVerification.configuration
        project.configurations
                .findAll { config -> config.name =~ configurationName }
                .collectMany { it.resolvedConfiguration.resolvedArtifacts }
    }
}