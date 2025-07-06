package com.kaolinmc.tooling.api.extension

import com.kaolinmc.boot.archive.ArchiveAccessTree
import com.kaolinmc.boot.archive.ArchiveNode
import com.kaolinmc.tooling.api.extension.artifact.ExtensionDescriptor
import com.kaolinmc.tooling.api.extension.partition.ExtensionPartitionContainer

public class ExtensionNode(
    override val descriptor: ExtensionDescriptor,
    override val access: ArchiveAccessTree,

    public val parents: List<ExtensionNode>,
    public val classLoader: ExtensionClassLoader,

    public val runtimeModel: ExtensionRuntimeModel
) : ArchiveNode<ExtensionDescriptor> {
//    public val partitions: List<ExtensionPartitionContainer<*, *>> by classLoader::partitions

    override fun toString(): String {
        return "Extension $descriptor"
    }
}