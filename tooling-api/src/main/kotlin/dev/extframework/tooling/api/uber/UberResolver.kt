package dev.extframework.tooling.api.uber

import com.durganmcbroom.artifact.resolver.Artifact
import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.artifact.resolver.ArtifactRequest
import com.durganmcbroom.artifact.resolver.RepositorySettings
import com.durganmcbroom.artifact.resolver.ResolutionContext
import com.durganmcbroom.artifact.resolver.createContext
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.async.AsyncJob
import com.durganmcbroom.jobs.async.asyncJob
import com.durganmcbroom.jobs.async.mapAsync
import com.durganmcbroom.jobs.job
import com.durganmcbroom.jobs.result
import dev.extframework.boot.archive.ArchiveAccessTree
import dev.extframework.boot.archive.ArchiveData
import dev.extframework.boot.archive.ArchiveNodeResolver
import dev.extframework.boot.archive.ArchiveRelationship
import dev.extframework.boot.archive.ArchiveTrace
import dev.extframework.boot.archive.CacheHelper
import dev.extframework.boot.archive.CachedArchiveResource
import dev.extframework.boot.archive.IArchive
import dev.extframework.boot.archive.ResolutionHelper
import dev.extframework.boot.monad.Tagged
import dev.extframework.boot.monad.Tree
import dev.extframework.boot.util.requireKeyInDescriptor
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
    override val context: ResolutionContext<UberRepositorySettings, UberArtifactRequest, UberArtifactMetadata> =
        UberRepositoryFactory.createContext()
    override val metadataType: Class<UberArtifactMetadata> = UberArtifactMetadata::class.java
    override val name: String = "uber-loader"
    override val nodeType: Class<in UberNode> = UberNode::class.java
    override val apiVersion: Int = 1

    // TODO this is somewhat hacky
    public val by: MutableMap<ArtifactMetadata.Descriptor, UberDescriptor> =
        HashMap<ArtifactMetadata.Descriptor, UberDescriptor>()

    override fun deserializeDescriptor(
        descriptor: Map<String, String>,
        trace: ArchiveTrace
    ): Result<UberDescriptor> = result {
        val name = descriptor.requireKeyInDescriptor("name") { trace }

        UberDescriptor(name)
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
    ): Job<UberNode> = job {
        accessTree
            .targets
            .filter { it.relationship is ArchiveRelationship.Direct }
            .map { it.relationship.node.descriptor }
            .forEach { desc ->
                by[desc] = data.descriptor
            }

        UberNode(accessTree, data.descriptor)
    }

    override fun cache(
        artifact: Artifact<UberArtifactMetadata>,
        helper: CacheHelper<UberDescriptor>
    ): AsyncJob<Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>> = asyncJob {
        fun <
                D : ArtifactMetadata.Descriptor,
                T : ArtifactRequest<D>,
                R : RepositorySettings
                > cacheReq(req: UberParentRequest<D, T, R>) = helper.cache(
            req.request,
            req.repository,
            req.resolver
        )

        artifact.metadata.requestedParents.forEach {
            by[it.request.descriptor] = artifact.metadata.descriptor
        }

        val parents = artifact.metadata.requestedParents.mapAsync {
            cacheReq(
                it
            )().merge()
        }

        helper.newData(
            artifact.metadata.descriptor,
            parents.awaitAll(),
        )
    }
}