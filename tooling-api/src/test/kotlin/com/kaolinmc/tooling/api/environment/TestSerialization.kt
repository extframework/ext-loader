package com.kaolinmc.tooling.api.environment

import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.kaolinmc.tooling.api.exception.InternalExceptions
import com.kaolinmc.tooling.api.exception.StructuredException
import com.kaolinmc.tooling.api.exception.handleException
import com.kaolinmc.tooling.api.exception.serializeInternal
import com.kaolinmc.tooling.api.extension.artifact.ExtensionDescriptor
import org.junit.jupiter.api.Test

class TestSerialization {
    @Test
    fun `Test repository serializer`() {
        val repo = SimpleMavenRepositorySettings.mavenCentral()

        println(serializeInternal(repo))
    }

    @Test
    fun `Test pair serializer`() {
        val repo = SimpleMavenRepositorySettings.mavenCentral()

        println(serializeInternal("Testing" to repo))
    }

    @Test
    fun `Test map serializer`() {
        val repo = SimpleMavenRepositorySettings.mavenCentral()

        println(serializeInternal(mapOf(ExtensionDescriptor.parseDescriptor("a:a:a") to repo)))
    }

    @Test
    fun `Test full exception serialization`() {
        val ex = StructuredException(InternalExceptions.PartitionLoadException) {
            val repo = SimpleMavenRepositorySettings.mavenCentral()

            mapOf(ExtensionDescriptor.parseDescriptor("a:a:a") to repo) asContext "Repos"
            listOf(ExtensionDescriptor.parseDescriptor("a:a:a") to repo) asContext "Something"
        }

        println(handleException(ex))
    }
}