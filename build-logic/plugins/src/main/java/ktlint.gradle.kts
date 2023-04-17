plugins {
    id("org.jlleitschuh.gradle.ktlint")
}

ktlint {
    // Use a newer version to resolve https://github.com/JLLeitschuh/ktlint-gradle/issues/507
    version.set("0.47.1")
}
