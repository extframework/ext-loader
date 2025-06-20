import dev.extframework.gradle.common.archives
import dev.extframework.gradle.common.extFramework
import dev.extframework.gradle.common.objectContainer

group = "dev.extframework"
version = "1.1-SNAPSHOT"

dependencies {
    implementation(objectContainer())
    implementation(archives())

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(project(":"))
}

tasks.test {
    useJUnitPlatform()
}

common {
    defaultJavaSettings()
    publishing {
        repositories {
            extFramework(credentials = propertyCredentialProvider)
        }

        publication {
            withJava()
            withSources()
            withDokka()

            commonPom {
                packaging = "jar"

                withExtFrameworkRepo()
                defaultDevelopers()
                gnuLicense()
                extFrameworkScm("ext-loader")
            }
        }
    }
}