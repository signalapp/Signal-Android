plugins {
  id("org.jetbrains.kotlin.jvm") version "2.2.20"
  `java-library`
}

version = "1.0.0"

base {
  archivesName.set("wire-handler")
}

repositories {
  mavenCentral()
}

dependencies {
  implementation("com.squareup.wire:wire-schema:6.4.0")
}
