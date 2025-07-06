import com.kaolinmc.gradle.common.*

group = "com.kaolinmc.test"

version = "1.0"

dependencies {
    implementation(objectContainer())
    implementation(archives())
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