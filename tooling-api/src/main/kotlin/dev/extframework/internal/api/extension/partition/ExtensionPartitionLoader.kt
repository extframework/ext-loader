package dev.extframework.internal.api.extension.partition

import com.durganmcbroom.artifact.resolver.Artifact
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.async.AsyncJob
import dev.extframework.archives.ArchiveHandle
import dev.extframework.archives.ArchiveReference
import dev.extframework.boot.archive.*
import dev.extframework.boot.loader.*
import dev.extframework.boot.monad.Tagged
import dev.extframework.boot.monad.Tree
import dev.extframework.internal.api.extension.*
import dev.extframework.internal.api.extension.artifact.ExtensionArtifactMetadata
import dev.extframework.internal.api.extension.partition.artifact.PartitionArtifactMetadata
import dev.extframework.internal.api.extension.partition.artifact.PartitionDescriptor
import java.security.ProtectionDomain

public interface ExtensionPartition {
    public val archive: ArchiveHandle
    public val access: PartitionAccessTree
}

public interface PartitionAccessTree : ArchiveAccessTree {
    public val partitions: List<ExtensionPartitionContainer<*, *>>
}

public interface PartitionLoaderHelper {
    public val parentClassLoader: ClassLoader
    public val erm: ExtensionRuntimeModel

    public fun metadataFor(
        reference: PartitionModelReference
    ) : AsyncJob<ExtensionPartitionMetadata>

    public operator fun get(name: String): CachedArchiveResource?
}

public interface PartitionMetadataHelper {
    public val erm: ExtensionRuntimeModel
}

public interface ExtensionPartitionMetadata {
    public val name: String
}

public interface PartitionCacheHelper : CacheHelper<PartitionDescriptor> {
    public val parents: Map<ExtensionParent, ExtensionArtifactMetadata>
    public val erm: ExtensionRuntimeModel

    public fun newPartition(
        partition: PartitionRuntimeModel,
    ) : AsyncJob<Tree<Tagged<ArchiveData<*, *>, ArchiveNodeResolver<*, *, *, *, *>>>>

    public fun cache(
        reference: PartitionModelReference
    ): AsyncJob<Tree<Tagged<ArchiveData<*, *>, ArchiveNodeResolver<*, *, *, *, *>>>>

    public fun cache(
        parent: ExtensionParent,
        partition: PartitionModelReference,
    ) : AsyncJob<Tree<Tagged<ArchiveData<*, *>, ArchiveNodeResolver<*, *, *, *, *>>>>
}

public interface ExtensionPartitionLoader<T : ExtensionPartitionMetadata> {
    public val type: String

    public fun parseMetadata(
        partition: PartitionRuntimeModel,
        reference: ArchiveReference,
        helper: PartitionMetadataHelper
    ): Job<T>

    public fun load(
        metadata: T,
        reference: ArchiveReference,
        accessTree: PartitionAccessTree,
        helper: PartitionLoaderHelper
    ): Job<ExtensionPartitionContainer<*, T>>

    public fun cache(
        artifact: Artifact<PartitionArtifactMetadata>,
        helper: PartitionCacheHelper
    ) : AsyncJob<Tree<Tagged<ArchiveData<*, *>, ArchiveNodeResolver<*, *, *, *, *>>>>
}