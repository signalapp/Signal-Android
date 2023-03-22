

plugins {
    `kotlin-dsl`
    id("groovy-gradle-plugin")
    id("org.jlleitschuh.gradle.ktlint") version "11.1.0"
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlinDslPluginOptions {
    jvmTarget.set("11")
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.android.library)
    implementation(libs.android.application)
    implementation(project(":tools"))
    implementation(libs.ktlint)

    // These allow us to reference the dependency catalog inside of our compiled plugins
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
    implementation(files(testLibs.javaClass.superclass.protectionDomain.codeSource.location))
}

ktlint {
    filter {
        exclude { element ->
            element.file.path.contains("/build/generated-sources")
        }
    }
}
