import dev.extframework.gradle.common.*

plugins {
    kotlin("jvm") version "2.1.20"

    id("dev.extframework.common") version "1.1"
}

group = "dev.extframework"
version = "2.2-SNAPSHOT"

tasks.wrapper {
    gradleVersion = "8.3"
}

dependencies {
    testImplementation(project(":"))

    implementation(archives())
    implementation(commonUtil())
    implementation(objectContainer())

    implementation(project(":tooling-api"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")

    testImplementation("net.bytebuddy:byte-buddy-agent:1.14.17")
    testImplementation(kotlin("test"))
    // https://mvnrepository.com/artifact/junit/junit
    testImplementation("junit:junit:4.13.2")
}

common {
    publishing {
        publication {
            artifactId = "ext-loader"
        }
    }
}


abstract class ListAllDependencies : DefaultTask() {
    @get:OutputFile
    abstract val output: RegularFileProperty

    @TaskAction
    fun listDependencies() {
        val outputFile = output.get().asFile
        // Ensure the directory for the output file exists
        outputFile.parentFile.mkdirs()
        // Clear or create the output file
        outputFile.writeText("")

        val set = HashSet<String>()

        // Process each configuration that can be resolved
        project.configurations.filter { it.isCanBeResolved }.forEach { configuration ->
            println("Processing configuration: ${configuration.name}")
            try {
                configuration.resolvedConfiguration.firstLevelModuleDependencies.forEach { dependency ->
                    collectDependencies(dependency, set)
                }
            } catch (e: Exception) {
                println("Skipping configuration '${configuration.name}' due to resolution errors.")
            }
        }

        set.add("${this.project.group}:minecraft-bootstrapper:${this.project.version}\n")

        set.forEach {
            outputFile.appendText(it)
        }
    }

    private fun collectDependencies(dependency: ResolvedDependency, set: MutableSet<String>) {
        set.add("${dependency.moduleGroup}:${dependency.moduleName}:${dependency.moduleVersion}\n")
        dependency.children.forEach { childDependency ->
            collectDependencies(childDependency, set)
        }
    }
}

val listDependencies by tasks.registering(ListAllDependencies::class) {
    output.set(project.layout.buildDirectory.file("resources/test/dependencies.txt"))
}

tasks.test {
    dependsOn(listDependencies)

    dependsOn(project(":blackbox").tasks.named("clean"))
    dependsOn(project(":blackbox").tasks.named("publishToMavenLocal"))
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

allprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "dev.extframework.common")

    repositories {
        mavenCentral()
        extFramework()
    }

    kotlin {
        explicitApi()
        jvmToolchain(8)
        compilerOptions {
            freeCompilerArgs.addAll(listOf("-Xjvm-default", "all"))
        }
    }

    dependencies {
        implementation(artifactResolver())
        implementation(boot())
        implementation(kotlin("stdlib"))
        implementation(kotlin("reflect"))
        testImplementation(kotlin("test"))
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(8))
        }
    }
}