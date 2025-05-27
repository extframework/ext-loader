pluginManagement {
    repositories {
        maven {
            url = uri("https://maven.extframework.dev/releases")
        }
        maven {
            url = uri("https://maven.extframework.dev/snapshots")
        }
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "ext-loader"

include("tooling-api")
include("blackbox")
findProject(":blackbox")?.projectDir = file("tests/blackbox")