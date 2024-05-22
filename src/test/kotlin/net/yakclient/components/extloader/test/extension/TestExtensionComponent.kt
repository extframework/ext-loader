package net.yakclient.components.extloader.test.extension

import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.artifact.resolver.simple.maven.layout.mavenLocal
import net.yakclient.boot.component.artifact.SoftwareComponentDescriptor
import net.yakclient.boot.component.context.ContextNodeTypes
import net.yakclient.boot.test.testBootInstance
import net.yakclient.common.util.resolve
import net.yakclient.components.extloader.ExtLoaderConfiguration
import net.yakclient.components.extloader.ExtLoaderEnvironmentConfiguration
import net.yakclient.components.extloader.ExtLoaderEnvironmentType
import net.yakclient.components.extloader.ExtensionLoaderFactory
import net.yakclient.minecraft.bootstrapper.MinecraftBootstrapperFactory
import runBootBlocking
import java.io.InputStreamReader
import java.nio.file.Path
import kotlin.test.Test

fun main() {
    TestExtensionComponent().`Load extension`()
}

class TestExtensionComponent {
    private fun readDependenciesList(): Set<String> {
        val ins = this::class.java.getResourceAsStream("/dependencies.txt")!!
        return InputStreamReader(ins).use {
            it.readLines().toSet()
        }
    }

    @Test
    fun `Load extension`() {
        val cache = Path.of(System.getProperty("user.dir")) resolve "src" resolve "test" resolve "resources" resolve "run-cache"

        val dependencies = readDependenciesList().mapTo(HashSet()) { SimpleMavenDescriptor.parseDescription(it)!! }
            .filterNotTo(HashSet()) { it.artifact == "minecraft-bootstrapper" }

        runBootBlocking {
            val boot = testBootInstance(
                mapOf(
                    SoftwareComponentDescriptor(
                        "net.yakclient.components",
                        "minecraft-bootstrapper",
                        "1.0-SNAPSHOT", null
                    ) to MinecraftBootstrapperFactory::class.java
                ), cache,
                dependencies = dependencies
            )

            val value = mapOf(
                "extension" to mapOf(
                    "descriptor" to mapOf(
                        "groupId" to "net.yakclient.extensions",
                        "artifactId" to "example-extension",
                        "version" to "1.0-SNAPSHOT"
                    ),
                    "repository" to mapOf(
                        "type" to "local",
                        "location" to mavenLocal
                    )
                ),
                "mappingType" to "mojang:deobfuscated"
            )

            val instance = ExtensionLoaderFactory(boot).new(
                ExtLoaderConfiguration(
                    "1.20.1",
                    listOf("--accessToken", ""),
                    ExtLoaderEnvironmentConfiguration(
                        ExtLoaderEnvironmentType.INTERNAL_DEV,
                        ContextNodeTypes.newValueType(value)
                    )
                )
            )

            instance.start()().merge()
        }
    }
}