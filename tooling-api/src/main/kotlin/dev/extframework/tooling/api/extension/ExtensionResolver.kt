package dev.extframework.tooling.api.extension

import dev.extframework.boot.archive.ArchiveNodeResolver
import dev.extframework.boot.archive.ArchiveTrace
import dev.extframework.boot.util.requireKeyInDescriptor
import dev.extframework.tooling.api.extension.artifact.ExtensionArtifactMetadata
import dev.extframework.tooling.api.extension.artifact.ExtensionArtifactRequest
import dev.extframework.tooling.api.extension.artifact.ExtensionDescriptor
import dev.extframework.tooling.api.extension.artifact.ExtensionRepositorySettings
import dev.extframework.tooling.api.extension.partition.PartitionResolver
import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path

public interface ExtensionResolver : ArchiveNodeResolver<
        ExtensionDescriptor,
        ExtensionArtifactRequest,
        ExtensionNode,
        ExtensionRepositorySettings,
        ExtensionArtifactMetadata> {
    public val layerLoader: ClassLoader
    public val partitionResolver: PartitionResolver

    override val id: String
        get() = "extension"

    override val metadataType: Class<ExtensionArtifactMetadata>
        get() = ExtensionArtifactMetadata::class.java
    override val nodeType: Class<ExtensionNode>
        get() = ExtensionNode::class.java

    public val accessBridge: AccessBridge

    override fun deserializeDescriptor(
        descriptor: Map<String, String>,
        trace: ArchiveTrace
    ): ExtensionDescriptor {
        return ExtensionDescriptor.parseDescriptor(descriptor.requireKeyInDescriptor("descriptor") { trace })
    }

    override fun serializeDescriptor(
        descriptor: ExtensionDescriptor
    ): Map<String, String> {
        return mapOf(
            "descriptor" to descriptor.name,
        )
    }

    override fun pathForDescriptor(descriptor: ExtensionDescriptor, classifier: String, type: String): Path {
        return Path(
            descriptor.group.replace('.', File.separatorChar),
            descriptor.artifact,
            descriptor.version,
            "${descriptor.artifact}-${descriptor.version}-$classifier.$type"
        )
    }

    public interface AccessBridge {
        public fun classLoaderFor(
            descriptor: ExtensionDescriptor,
        ): ExtensionClassLoader

        public fun ermFor(
            descriptor: ExtensionDescriptor,
        ): ExtensionRuntimeModel

        // Once an extension is loaded, it should never have any part of it loaded
        // from another repository (Both for code security and to maximize capability
        // if for whatever reason different repositories have different code). Because
        // of this it is safe to always associate 1 repository with a whole extension.
        public fun repositoryFor(
            descriptor: ExtensionDescriptor,
        ): ExtensionRepositorySettings
    }
}
