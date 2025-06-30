package dev.extframework.tooling.api.extension.partition

import dev.extframework.archives.ArchiveHandle
import dev.extframework.archives.ArchiveReference
import dev.extframework.boot.archive.*
import dev.extframework.boot.monad.Either
import dev.extframework.boot.monad.Tagged
import dev.extframework.boot.monad.Tree
import dev.extframework.`object`.ObjectContainer
import dev.extframework.tooling.api.extension.ExtensionParent
import dev.extframework.tooling.api.extension.ExtensionRuntimeModel
import dev.extframework.tooling.api.extension.PartitionRuntimeModel
import dev.extframework.tooling.api.extension.partition.artifact.PartitionArtifactMetadata
import dev.extframework.tooling.api.extension.partition.artifact.PartitionDescriptor

public interface ExtensionPartition {
    public val archive: ArchiveHandle?
    public val access: PartitionAccessTree
}

public interface PartitionAccessTree : ArchiveAccessTree {
    public val partitions: List<ExtensionPartitionContainer<*, *>>
}

public interface PartitionLoaderHelper {
    public val parentClassLoader: ClassLoader
    public val erm: ExtensionRuntimeModel
    public val descriptor: PartitionDescriptor

    public fun metadataFor(
        partition: String
    ): ExtensionPartitionMetadata

    public operator fun get(name: String): CachedArchiveResource?
}

public interface PartitionMetadataHelper {
    public val erm: ExtensionRuntimeModel
}

public interface ExtensionPartitionMetadata {
    public val name: String
}

public interface PartitionCacheHelper : CacheHelper<PartitionDescriptor> {
    //    public val parents: Map<ExtensionParent, ExtensionArtifactMetadata>
    public val erm: ExtensionRuntimeModel
    public val prm: PartitionRuntimeModel
//    public val defaultEnvironment: String

//    public fun newPartition(
//        partition: PartitionRuntimeModel,
//    ) : AsyncJob<Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>>

    public suspend fun cache(
        partition: String,
//        environment: String
    ): Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>

    public suspend fun cache(
        partition: String,
//        environment: String,
        parent: ExtensionParent,
    ): Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>
}

//public val PartitionCacheHelper.currentEnvironment: String
//    get() =

public interface ExtensionPartitionLoader<T : ExtensionPartitionMetadata>: ObjectContainer.IDed {
    override val id: String

    public fun parseMetadata(
        partition: PartitionRuntimeModel,
        reference: ArchiveReference?,
        helper: PartitionMetadataHelper
    ): T

    // TODO decide if reference should remain as a parameter here or should be forced to be included
    //   as a property in T. Reasoning behind this is that in both Main and Target partitions this reference
    //   value is discarded and instead the reference in metadata is consumed. This only (actually) matters
    //   for the target partition where then if remapping occurs under Minecraft, mixins dont properly apply
    //   because the used reference is the one provided here, NOT the one provided in parse metadata. Another
    //   option is to cache loaded references in the PartitionResolver as have the same object passed to each method.
    public fun load(
        metadata: T,
        reference: ArchiveReference?,
        accessTree: PartitionAccessTree,
        helper: PartitionLoaderHelper
    ): ExtensionPartitionContainer<*, T>

    public suspend fun cache(
        metadata: PartitionArtifactMetadata,
        parents: List<Tree<Either<PartitionArtifactMetadata, TaggedIArchive>>>,
        helper: PartitionCacheHelper
    ): Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>
}