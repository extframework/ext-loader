package dev.extframework.tooling.api.extension.partition

import com.durganmcbroom.jobs.result
import dev.extframework.boot.archive.ArchiveNodeResolver
import dev.extframework.boot.archive.ArchiveTrace
import dev.extframework.boot.util.requireKeyInDescriptor
import dev.extframework.boot.util.typeOf
import dev.extframework.tooling.api.extension.artifact.ExtensionDescriptor
import dev.extframework.tooling.api.extension.artifact.ExtensionRepositorySettings
import dev.extframework.tooling.api.extension.partition.artifact.PartitionArtifactMetadata
import dev.extframework.tooling.api.extension.partition.artifact.PartitionArtifactRequest
import dev.extframework.tooling.api.extension.partition.artifact.PartitionDescriptor
import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path

public interface PartitionResolver : ArchiveNodeResolver<
        PartitionDescriptor, PartitionArtifactRequest, ExtensionPartitionContainer<*, *>, ExtensionRepositorySettings, PartitionArtifactMetadata> {
    override val metadataType: Class<PartitionArtifactMetadata>
        get() = PartitionArtifactMetadata::class.java
    override val nodeType: Class<in ExtensionPartitionContainer<*, *>>
        get() = typeOf()
    override val apiVersion: Int
        get() = 1

    override fun deserializeDescriptor(
        descriptor: Map<String, String>,
        trace: ArchiveTrace
    ): Result<PartitionDescriptor> = result {
        PartitionDescriptor(
            ExtensionDescriptor(
                descriptor.requireKeyInDescriptor("group") { trace },
                descriptor.requireKeyInDescriptor("artifact") { trace },
                descriptor.requireKeyInDescriptor("version") { trace },
            ),
            descriptor.requireKeyInDescriptor("partition") { trace },
            descriptor.requireKeyInDescriptor("environment") { trace }
        )
    }

    override fun serializeDescriptor(descriptor: PartitionDescriptor): Map<String, String> {
        return mapOf(
            "group" to descriptor.extension.group,
            "artifact" to descriptor.extension.artifact,
            "version" to descriptor.extension.version,
            "partition" to descriptor.partition,
            "environment" to descriptor.environment,
        )
    }

    override fun pathForDescriptor(descriptor: PartitionDescriptor, classifier: String, type: String): Path {
        return Path(
            descriptor.extension.group.replace('.', File.separatorChar),
            descriptor.extension.artifact,
            descriptor.extension.version,
            descriptor.partition,
            "${descriptor.extension.artifact}-${descriptor.extension.version}-${descriptor.partition}-$classifier.$type"
        )
    }
}