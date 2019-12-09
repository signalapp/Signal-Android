package org.whispersystems.witness

import org.gradle.api.InvalidUserDataException
import org.gradle.api.Plugin
import org.gradle.api.Project
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
                    List   parts = assertion[0].tokenize(':')
                    String group = parts.get(0)
                    String name  = parts.get(1)
                    String hash  = assertion[1]

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
            def stringBuilder = new StringBuilder()

            stringBuilder.append '// Auto-generated, use ./gradlew calculateChecksums to regenerate\n\n'
            stringBuilder.append 'dependencyVerification {\n'

            stringBuilder.append '    verify = [\n'

            allArtifacts(project)
                    .findAll { dep -> !dep.id.componentIdentifier.displayName.startsWith('project :') }
                    .collect { dep -> "['$dep.moduleVersion.id.group:$dep.name:$dep.moduleVersion.id.version',\n         '${calculateSha256(dep.file)}']" }
                    .sort()
                    .each {
                        dep -> stringBuilder.append "\n        $dep,\n"
                    }

            stringBuilder.append "    ]\n"
            stringBuilder.append "}\n"

            project.file("witness-verifications.gradle").write(stringBuilder.toString())
        }
    }

    private static Set<ResolvedArtifact> allArtifacts(Project project) {
        def configurationName = project.dependencyVerification.configuration
        project.configurations
                .findAll { config -> config.name =~ configurationName }
                .collectMany { it.resolvedConfiguration.resolvedArtifacts }
    }
}