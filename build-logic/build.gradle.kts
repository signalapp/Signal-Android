import org.gradle.kotlin.dsl.extra

buildscript {
    val kotlinVersion by extra("1.9.20")

    repositories {
        google()
        mavenCentral()
    }

    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    }
}

apply(from = "${rootDir}/../constants.gradle.kts")

