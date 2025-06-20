import dev.extframework.gradle.common.archives
import dev.extframework.gradle.common.objectContainer

group = "dev.extframework.test"

version = "1.0"

dependencies {
    objectContainer()
    archives()
    implementation(project(":tooling-api"))
}

tasks.test {
    useJUnitPlatform()
}

common {
    defaultJavaSettings()
    publishing {
        publication {
            artifact(tasks.jar).classifier = "tweaker"
            artifact(projectDir.resolve("src/main/resources/erm.json")).classifier = "erm"
        }
    }
}