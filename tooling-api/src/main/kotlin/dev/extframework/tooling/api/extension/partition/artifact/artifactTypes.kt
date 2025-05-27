package dev.extframework.tooling.api.extension.partition.artifact

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.artifact.resolver.ArtifactRequest
import com.durganmcbroom.resources.Resource
import dev.extframework.tooling.api.extension.artifact.ExtensionArtifactRequest
import dev.extframework.tooling.api.extension.artifact.ExtensionDescriptor
import dev.extframework.tooling.api.extension.artifact.ExtensionRepositorySettings

public data class PartitionDescriptor(
    val extension: ExtensionDescriptor,
    val partition: String,
    val environment: String
) : ArtifactMetadata.Descriptor {
    override val name: String = "${extension.name}:$partition"

//    public companion object {
//        public fun parseDescriptor(name: String): PartitionDescriptor {
//            val (group, ext, version, partition) = name.split(":").takeIf { it.size == 4 }
//                ?: throw IllegalArgumentException("Invalid extension partition descriptor: $name")
//
//            return PartitionDescriptor(
//                ExtensionDescriptor(
//                    group, ext, version
//                ), partition
//            )
//        }
//    }

    override fun toString(): String = name
}

public data class PartitionArtifactRequest(
    override val descriptor: PartitionDescriptor,
) : ArtifactRequest<PartitionDescriptor> {
    public constructor(
        extensionReq: ExtensionArtifactRequest,
        partition: String,
        environment: String
    ) : this(PartitionDescriptor(extensionReq.descriptor, partition, environment))

    public constructor(
        extensionDescriptor: ExtensionDescriptor,
        partition: String,
        environment: String
    ) : this(PartitionDescriptor(extensionDescriptor, partition, environment))
}

public typealias PartitionParentInfo = ArtifactMetadata.ParentInfo<PartitionArtifactRequest, ExtensionRepositorySettings>

public class PartitionArtifactMetadata(
    desc: PartitionDescriptor,
    public val resource: Resource?,
) : ArtifactMetadata<PartitionDescriptor, PartitionParentInfo>(desc, listOf())

public fun ExtensionDescriptor.partition(name: String, environment: String): PartitionDescriptor {
    return PartitionDescriptor(this, name, environment)
}