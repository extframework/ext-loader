package com.kaolinmc.tooling.api.uber

import com.durganmcbroom.artifact.resolver.*
import com.kaolinmc.boot.archive.*
import com.kaolinmc.boot.monad.Either
import com.kaolinmc.boot.monad.Tagged
import com.kaolinmc.boot.monad.Tree
import com.kaolinmc.boot.util.mapAsync
import com.kaolinmc.boot.util.requireKeyInDescriptor
import kotlinx.coroutines.awaitAll
import java.nio.file.Path
import kotlin.io.path.Path

// Used to resolve a collection of many items at once. This both
// provides speed improvements (multiple concurrency) and the ability
// to run constraint auditing on the entire tree even if they are not
// technically related by a defined parent / child relationship.
public object UberResolver : ArchiveNodeResolver<
        UberDescriptor,
        UberArtifactRequest,
        UberNode,
        UberRepositorySettings,
        UberArtifactMetadata> {
    override val factory: UberRepositoryFactory = UberRepositoryFactory
    override val metadataType: Class<UberArtifactMetadata> = UberArtifactMetadata::class.java
    override val id: String = "uber-loader"
    override val nodeType: Class<in UberNode> = UberNode::class.java
    override val apiVersion: Int = 1

    // TODO this is somewhat hacky
    public val by: MutableMap<ArtifactMetadata.Descriptor, UberDescriptor> =
        HashMap()

    override fun deserializeDescriptor(
        descriptor: Map<String, String>,
        trace: ArchiveTrace
    ): UberDescriptor {
        val name = descriptor.requireKeyInDescriptor("name") { trace }

        return UberDescriptor(name)
    }

    override fun serializeDescriptor(descriptor: UberDescriptor): Map<String, String> {
        return mapOf("name" to descriptor.name)
    }

    override fun pathForDescriptor(
        descriptor: UberDescriptor,
        classifier: String,
        type: String
    ): Path {
        return Path("uber", descriptor.name, descriptor.randomId, "$classifier.$type")
    }

    override fun load(
        data: ArchiveData<UberDescriptor, CachedArchiveResource>,
        accessTree: ArchiveAccessTree,
        helper: ResolutionHelper
    ): UberNode {
        accessTree
            .targets
            .filter { it.relationship is ArchiveRelationship.Direct }
            .map { it.relationship.node.descriptor }
            .forEach { desc ->
                by[desc] = data.descriptor
            }

        return UberNode(accessTree, data.descriptor)
    }

    override suspend fun cache(
        metadata: UberArtifactMetadata,
        parents: List<Tree<Either<UberArtifactMetadata, TaggedIArchive>>>,
        helper: CacheHelper<UberDescriptor>
    ): Tree<TaggedIArchive> {
        suspend fun <
                D : ArtifactMetadata.Descriptor,
                T : ArtifactRequest<D>,
                R : RepositorySettings
                > cacheReq(req: UberParentRequest<D, T, R>) = helper.cache(
            req.request,
            req.repository,
            req.resolver
        )

        metadata.requestedParents.forEach {
            by[it.request.descriptor] = metadata.descriptor
        }

        val parents = metadata.requestedParents.mapAsync {
            cacheReq(
                it
            )
        }

        return helper.newData(
            metadata.descriptor,
            parents.awaitAll(),
        )
    }
}