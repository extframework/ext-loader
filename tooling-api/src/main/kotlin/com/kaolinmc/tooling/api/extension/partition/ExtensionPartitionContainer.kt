package com.kaolinmc.tooling.api.extension.partition

import com.kaolinmc.archives.ArchiveHandle
import com.kaolinmc.boot.archive.ClassLoadedArchiveNode
import com.kaolinmc.tooling.api.extension.partition.artifact.PartitionDescriptor

public interface ExtensionPartitionContainer<out T : ExtensionPartition,  out M : ExtensionPartitionMetadata> : ClassLoadedArchiveNode<PartitionDescriptor> {
    override val descriptor: PartitionDescriptor
    public val metadata: M
    public val node: T

    override val handle: ArchiveHandle?
        get() = node.archive
    override val access: PartitionAccessTree
        get() = node.access
}

public fun <T : ExtensionPartition, M : ExtensionPartitionMetadata> ExtensionPartitionContainer(
    descriptor: PartitionDescriptor,
    metadata: M,
    node: T,
): ExtensionPartitionContainer<T, M> =
    object : ExtensionPartitionContainer<T, M> {
        override val descriptor: PartitionDescriptor = descriptor
        override val metadata: M = metadata
        override val node: T = node
    }