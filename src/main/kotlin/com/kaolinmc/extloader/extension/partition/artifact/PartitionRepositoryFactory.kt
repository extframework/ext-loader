package com.kaolinmc.extloader.extension.partition.artifact

import com.durganmcbroom.artifact.resolver.RepositoryFactory
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.kaolinmc.extloader.extension.artifact.ExtensionRepositoryFactory
import com.kaolinmc.tooling.api.extension.ExtensionRepository
import com.kaolinmc.tooling.api.extension.PartitionRuntimeModel
import com.kaolinmc.tooling.api.extension.artifact.ExtensionRepositorySettings
import com.kaolinmc.tooling.api.extension.partition.artifact.PartitionDescriptor
import io.ktor.client.request.request

public class PartitionRepositoryFactory(
    private val prmProvider: (PartitionDescriptor, ExtensionRepositorySettings) -> PartitionRuntimeModel?,
) : RepositoryFactory<ExtensionRepositorySettings, PartitionArtifactRepository> {
    override fun createNew(settings: SimpleMavenRepositorySettings): PartitionArtifactRepository {
        return PartitionArtifactRepository(
            settings,
            prmProvider,
            this
        )
    }
}