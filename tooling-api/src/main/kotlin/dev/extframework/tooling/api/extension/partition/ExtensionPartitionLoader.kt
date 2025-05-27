package dev.extframework.tooling.api.extension.partition

import com.durganmcbroom.artifact.resolver.Artifact
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.async.AsyncJob
import dev.extframework.archives.ArchiveHandle
import dev.extframework.archives.ArchiveReference
import dev.extframework.boot.archive.*
import dev.extframework.boot.monad.Tagged
import dev.extframework.boot.monad.Tree
import dev.extframework.tooling.api.extension.*
import dev.extframework.tooling.api.extension.artifact.ExtensionArtifactMetadata
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
    ): Job<ExtensionPartitionMetadata>

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
    public val defaultEnvironment: String

//    public fun newPartition(
//        partition: PartitionRuntimeModel,
//    ) : AsyncJob<Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>>

    public fun cache(
        partition: String,
        environment: String
    ): AsyncJob<Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>>

    public fun cache(
        partition: String,
        environment: String,
        parent: ExtensionParent,
    ): AsyncJob<Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>>
}

//public val PartitionCacheHelper.currentEnvironment: String
//    get() =

public interface ExtensionPartitionLoader<T : ExtensionPartitionMetadata> {
    public val type: String

    public fun parseMetadata(
        partition: PartitionRuntimeModel,
        reference: ArchiveReference?,
        helper: PartitionMetadataHelper
    ): Job<T>

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
    ): Job<ExtensionPartitionContainer<*, T>>

    public fun cache(
        artifact: Artifact<PartitionArtifactMetadata>,
        helper: PartitionCacheHelper
    ): AsyncJob<Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>>
}