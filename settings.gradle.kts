pluginManagement {
    repositories {
        maven {
            url = uri("https://maven.extframework.dev/releases")
        }
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "ext-loader"

include(":client-api")
include("blackbox-test")
include("tooling-api")
include("core")
include("core-mc")

include("core:blackbox-init-ext")
findProject(":core:blackbox-init-ext")?.name = "core-blackbox-init-ext"

include("core:blackbox-feature-ext")
findProject(":core:blackbox-feature-ext")?.name = "core-blackbox-feature-ext"

include("core:blackbox-app")
findProject(":core:blackbox-app")?.name = "core-blackbox-app"

include("core:blackbox-app-ext")
findProject(":core:blackbox-app-ext")?.name = "core-blackbox-app-ext"

include("core:blackbox-feature-delegation-ext")
findProject(":core:blackbox-feature-delegation-ext")?.name = "core-blackbox-feature-delegation-ext"

include("core:blackbox-link-ext")
findProject(":core:blackbox-link-ext")?.name = "core-blackbox-link-ext"

include("tests")

include("core:api")
findProject(":core:api")?.name = "core-api"


