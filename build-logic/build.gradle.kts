import org.gradle.kotlin.dsl.extra

buildscript {
    val kotlinVersion by extra("1.8.10")

    repositories {
        google()
        mavenCentral()
    }

    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    }
}

apply(from = "${rootDir}/../constants.gradle.kts")

val signalKotlinJvmTarget: String by extra

allprojects {
    // Needed because otherwise the kapt task defaults to jvmTarget 17, which "poisons the well" and requires us to bump up too
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = signalKotlinJvmTarget
        }
    }
}
