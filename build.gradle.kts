import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


plugins {
    kotlin("jvm") version "1.7.10"
    id("org.javamodularity.moduleplugin") version "1.8.12"

    id("signing")
    id("maven-publish")
    id("org.jetbrains.dokka") version "1.6.0"
}


configurations.all {
    resolutionStrategy.cacheChangingModulesFor(0, "seconds")
}

dependencies {
    implementation("net.yakclient:archive-mapper:1.0-SNAPSHOT")
    implementation("com.durganmcbroom:event-api:1.0-SNAPSHOT")
    implementation("io.arrow-kt:arrow-core:1.1.2")

    api("net.yakclient:archives-mixin:1.0-SNAPSHOT") {
        isChanging = true
    }
    implementation("net.yakclient:boot:1.0-SNAPSHOT") {
        exclude(group = "com.durganmcbroom", module = "artifact-resolver")
        exclude(group = "com.durganmcbroom", module = "artifact-resolver-simple-maven")

        exclude(group = "com.durganmcbroom", module = "artifact-resolver-jvm")
        exclude(group = "com.durganmcbroom", module = "artifact-resolver-simple-maven-jvm")
        isChanging = true
    }
    implementation("com.durganmcbroom:artifact-resolver:1.0-SNAPSHOT") {
        isChanging = true
    }
    implementation("com.durganmcbroom:artifact-resolver-simple-maven:1.0-SNAPSHOT") {
        isChanging = true
    }
    implementation("net.yakclient:common-util:1.0-SNAPSHOT") {
        isChanging = true
    }
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.4")
    implementation("net.yakclient.components:minecraft-bootstrapper:1.0-SNAPSHOT") {
        isChanging = true
    }

    testImplementation(kotlin("test"))

}

tasks.test {
    jvmArgs = listOf(
//        "--add-reads",
//        "yakclient.minecraft.provider._default=yakclient.archive.mapper",
        "--add-reads",
        "kotlin.stdlib=kotlinx.coroutines.core.jvm"
    )
}

task<Jar>("sourcesJar") {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

task<Jar>("javadocJar") {
    archiveClassifier.set("javadoc")
    from(tasks.dokkaJavadoc)
}

publishing {
    publications {
        create<MavenPublication>("yakclient-maven") {
            from(components["java"])
            artifact(tasks["sourcesJar"])
            artifact(tasks["javadocJar"])
            artifact("${sourceSets.main.get().resources.srcDirs.first().absoluteFile}${File.separator}component-model.json").classifier =
                "component-model"

            artifactId = "yakclient"

            pom {
                name.set("YakClient")
                description.set("The YakClient component for the boot module.")
                url.set("https://github.com/yakclient/yakclient")

                packaging = "jar"

                developers {
                    developer {
                        id.set("Chestly")
                        name.set("Durgan McBroom")
                    }
                }

                licenses {
                    license {
                        name.set("GNU General Public License")
                        url.set("https://opensource.org/licenses/gpl-license")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/yakclient/yakclient")
                    developerConnection.set("scm:git:ssh://github.com:yakclient/yakclient.git")
                    url.set("https://github.com/yakclient/yakclient")
                }
            }
        }
    }
}

allprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "maven-publish")

    group = "net.yakclient.components"
    version = "1.0-SNAPSHOT"

    repositories {
        mavenCentral()
        mavenLocal()
        maven {
            isAllowInsecureProtocol = true
            url = uri("http://maven.yakclient.net/snapshots")
        }
        maven {
            name = "Durgan McBroom GitHub Packages"
            url = uri("https://maven.pkg.github.com/durganmcbroom/artifact-resolver")
            credentials {
                username = project.findProperty("dm.gpr.user") as? String
                    ?: throw IllegalArgumentException("Need a Github package registry username!")
                password = project.findProperty("dm.gpr.key") as? String
                    ?: throw IllegalArgumentException("Need a Github package registry key!")
            }
        }
    }

    publishing {
        repositories {
            if (!project.hasProperty("maven-user") || !project.hasProperty("maven-pass")) return@repositories

            maven {
                val repo = if (project.findProperty("isSnapshot") == "true") "snapshots" else "releases"

                isAllowInsecureProtocol = true

                url = uri("http://maven.yakclient.net/$repo")

                credentials {
                    username = project.findProperty("maven-user") as String
                    password = project.findProperty("maven-pass") as String
                }
                authentication {
                    create<BasicAuthentication>("basic")
                }
            }
        }
    }

    kotlin {
        explicitApi()
    }

    dependencies {
        implementation(kotlin("stdlib"))
        implementation(kotlin("reflect"))
        testImplementation(kotlin("test"))
    }

    tasks.compileJava {
        destinationDirectory.set(destinationDirectory.asFile.get().resolve("main"))
        targetCompatibility = "17"
        sourceCompatibility = "17"
    }

    tasks.compileKotlin {
        destinationDirectory.set(tasks.compileJava.get().destinationDirectory.asFile.get())

        kotlinOptions {
            jvmTarget = "17"
        }
    }

    tasks.compileTestJava {
        destinationDirectory.set(destinationDirectory.asFile.get().resolve("test"))
    }

    tasks.compileTestKotlin {
        destinationDirectory.set(tasks.compileTestJava.get().destinationDirectory.asFile.get())

        kotlinOptions {
            jvmTarget = "17"
        }
    }

    tasks.test {
        useJUnitPlatform()
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }
}