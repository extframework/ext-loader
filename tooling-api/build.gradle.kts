import com.kaolinmc.gradle.common.*

group = "com.kaolinmc"
version = "1.1.3-SNAPSHOT"

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
            kaolin(credentials = propertyCredentialProvider)
        }

        publication {
            withJava()
            withSources()
            withDokka()

            commonPom {
                packaging = "jar"

                withKaolinRepo()
                defaultDevelopers()
                gnuLicense()
                kaolinScm("ext-loader")
            }
        }
    }
}