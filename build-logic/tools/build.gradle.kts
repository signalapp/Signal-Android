plugins {
    id("org.jetbrains.kotlin.jvm")
    id("java-library")
    id("org.jlleitschuh.gradle.ktlint") version "11.1.0"
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

// NOTE: For now, in order to run ktlint on this project, you have to manually run ./gradlew :build-logic:tools:ktlintFormat
//       Gotta figure out how to get it auto-included in the normal ./gradlew ktlintFormat
ktlint {
    // Use a newer version to resolve https://github.com/JLLeitschuh/ktlint-gradle/issues/507
    version.set("0.47.1")
}

dependencies {
    implementation(libs.dnsjava)
    testImplementation(testLibs.junit.junit)
    testImplementation(testLibs.mockk)
}
