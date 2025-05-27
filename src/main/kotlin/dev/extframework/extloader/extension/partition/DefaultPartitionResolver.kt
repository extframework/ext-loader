package dev.extframework.extloader.extension.partition

import com.durganmcbroom.artifact.resolver.*
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.async.AsyncJob
import com.durganmcbroom.jobs.async.asyncJob
import com.durganmcbroom.jobs.job
import com.durganmcbroom.resources.Resource
import dev.extframework.archives.ArchiveReference
import dev.extframework.archives.Archives
import dev.extframework.archives.zip.ZipFinder
import dev.extframework.boot.archive.*
import dev.extframework.boot.audit.Auditors
import dev.extframework.boot.constraint.registerConstraintNegotiator
import dev.extframework.boot.monad.Tagged
import dev.extframework.boot.monad.Tree
import dev.extframework.common.util.filterDuplicates
import dev.extframework.extloader.extension.ExtensionConstraintNegotiator
import dev.extframework.extloader.extension.ExtensionLoadException
import dev.extframework.extloader.extension.partition.artifact.PartitionRepositoryFactory
import dev.extframework.tooling.api.TOOLING_API_VERSION
import dev.extframework.tooling.api.environment.EnvironmentRegistry
import dev.extframework.tooling.api.environment.dependencyTypesAttrKey
import dev.extframework.tooling.api.environment.partitionLoadersAttrKey
import dev.extframework.tooling.api.exception.InternalExceptions
import dev.extframework.tooling.api.exception.StructuredException
import dev.extframework.tooling.api.extension.*
import dev.extframework.tooling.api.extension.artifact.ExtensionDescriptor
import dev.extframework.tooling.api.extension.artifact.ExtensionRepositorySettings
import dev.extframework.tooling.api.extension.partition.*
import dev.extframework.tooling.api.extension.partition.artifact.PartitionArtifactMetadata
import dev.extframework.tooling.api.extension.partition.artifact.PartitionArtifactRequest
import dev.extframework.tooling.api.extension.partition.artifact.PartitionDescriptor
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking

public open class DefaultPartitionResolver(
    private val bridge: ExtensionResolver.AccessBridge,
    private val environmentRegistry: EnvironmentRegistry,
    private val defaultEnvironment: String
) : PartitionResolver, RegisterAuditor {
    private val factory = PartitionRepositoryFactory { p, settings ->
        bridge.ermFor(p.extension).partitions.find {
            it.name == p.partition
        }?.takeIf { bridge.repositoryFor(p.extension) == settings }
    }

    override val apiVersion: Int = TOOLING_API_VERSION
    override val context: ResolutionContext<ExtensionRepositorySettings, PartitionArtifactRequest, PartitionArtifactMetadata>
        get() = factory.createContext()
    override val name: String
        get() = "extension-partition"

    override fun register(auditors: Auditors): Auditors {
        return auditors.registerConstraintNegotiator(
            ExtensionConstraintNegotiator(
                PartitionDescriptor::class.java, {
                    "${it.extension.group}:${it.extension.artifact}:${it.partition}"
                }
            ) {
                SimpleMavenDescriptor(
                    it.extension.group,
                    it.extension.artifact,
                    it.extension.version,
                    null
                )
            }
        )
    }

    protected fun unknownEnvironment(
        env: String
    ): Nothing = throw StructuredException(
        InternalExceptions.UnknownEnvironmentException,
        message = "Unknown environment $env"
    ) {
        solution("Please registry this environment with the EnvironmentRegistry.")
        environmentRegistry.objects().keys asContext "Registered environments"
    }

    protected fun getLoader(
        prm: PartitionRuntimeModel,
        env: String
    ): ExtensionPartitionLoader<ExtensionPartitionMetadata> {
        val environment = environmentRegistry.get(env) ?: unknownEnvironment(env)
        val partitionLoaders = environment[partitionLoadersAttrKey]?.container

        return (partitionLoaders?.get(prm.type) as? ExtensionPartitionLoader<ExtensionPartitionMetadata>)
            ?: throw IllegalArgumentException(
                "Illegal partition type: '${prm.type}', only accepted ones are: '${
                    partitionLoaders?.objects()?.map(
                        Map.Entry<String, ExtensionPartitionLoader<*>>::key
                    ) ?: listOf()
                }'"
            )
    }

    protected val parsedMetadata: MutableMap<Pair<ExtensionDescriptor, String>, ExtensionPartitionMetadata> = HashMap()

    protected fun parseMetadata(
        loader: ExtensionPartitionLoader<ExtensionPartitionMetadata>,
        prm: PartitionRuntimeModel,
        erm: ExtensionRuntimeModel,
        archive: ArchiveReference?
    ): Job<ExtensionPartitionMetadata> = job {
        parsedMetadata[erm.descriptor to (prm.name)] ?: loader.parseMetadata(
            prm,
            archive,
            object : PartitionMetadataHelper {
                override val erm: ExtensionRuntimeModel = erm
            }
        )().merge().also {
            parsedMetadata[erm.descriptor to (prm.name)] = it
        }
    }

    override fun load(
        data: ArchiveData<PartitionDescriptor, CachedArchiveResource>,
        accessTree: ArchiveAccessTree,
        helper: ResolutionHelper
    ): Job<ExtensionPartitionContainer<*, *>> = job {
        val archive = data.resources["partition.jar"]?.path?.let { Archives.find(it, ZipFinder) }
        val erm = bridge.ermFor(data.descriptor.extension)
        // Should never be null if getting to this stage.
        val prm = erm.partitions.find { it.name == data.descriptor.partition }!!

        val loader = getLoader(prm, data.descriptor.environment)
        val metadata = parseMetadata(loader, erm.partitions.find {
            it.name == prm.name
        } ?: throw PartitionLoadException(prm.name, "Partition not defined in the erm!") {
            erm.descriptor asContext "Extension"
        }, erm, archive)().merge()

        val parentLoader = bridge.classLoaderFor(data.descriptor.extension)

        loader.load(
            metadata,
            archive,
            object : PartitionAccessTree {
                override val partitions: List<ExtensionPartitionContainer<*, *>> =
                    accessTree.targets.map { it.relationship.node }
                        .filterIsInstance<ExtensionPartitionContainer<*, *>>()

                override val descriptor: ArtifactMetadata.Descriptor = data.descriptor
                override val targets: List<ArchiveTarget> = accessTree.targets
            },
            object : PartitionLoaderHelper {
                override val parentClassLoader: ClassLoader = parentLoader
                override val erm: ExtensionRuntimeModel = erm
                override val descriptor: PartitionDescriptor = data.descriptor

                override fun metadataFor(
                    partition: String
                ): Job<ExtensionPartitionMetadata> = job() {
                    parsedMetadata[erm.descriptor to (partition)]
                        ?: throw ExtensionLoadException(
                            data.descriptor.extension,
                            message = "Partition loader attempting to retrieve metadata for an extension partition that has not yet been cached."
                        ) {
                            solution("Cache the requested partition in the cache method of your partition loader.")

                            prm.name asContext "Partition name"
                            partition asContext "Requested partition name"
                        }
                }

                override fun get(name: String): CachedArchiveResource? {
                    return data.resources[name]
                }
            }
        )().merge()
    }

    override fun cache(
        artifact: Artifact<PartitionArtifactMetadata>,
        helper: CacheHelper<PartitionDescriptor>
    ): AsyncJob<Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>> = asyncJob {
        val descriptor = artifact.metadata.descriptor
        val erm = bridge.ermFor(descriptor.extension)
        val prm = erm.namedPartitions[descriptor.partition]
            ?: throw PartitionLoadException(
                descriptor.partition,
                "Unknown partition: '${descriptor.partition}'. It was not defined by this extensions runtime model."
            ) {
                erm.descriptor asContext "Extension name"
            }
        val loader = getLoader(prm, descriptor.environment)
        val environment = environmentRegistry.get(descriptor.environment)
            ?: unknownEnvironment(descriptor.environment)
        val dependencyTypes = environment[dependencyTypesAttrKey]!!.container

        helper.withResource("partition.jar", artifact.metadata.resource)

        val dependencies = cachePartitionDependencies(
            prm,
            descriptor.extension.artifact,
            dependencyTypes,
            helper
        )().merge()

        loader.cache(
            artifact,
            DefaultPartitionCacheHelper(
                erm, prm, helper, descriptor, dependencies
            )
        )().merge()
    }

    private inner class DefaultPartitionCacheHelper(
        override val erm: ExtensionRuntimeModel,
        override val prm: PartitionRuntimeModel,
        private val helper: CacheHelper<PartitionDescriptor>,
        private val descriptor: PartitionDescriptor,
        private val dependencies: List<Deferred<Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>>>
    ) : PartitionCacheHelper {
        override val defaultEnvironment: String = this@DefaultPartitionResolver.defaultEnvironment

        override fun cache(
            reference: String,
            environment: String
        ): AsyncJob<Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>> {
            return cache(
                PartitionArtifactRequest(
                    PartitionDescriptor(
                        descriptor.extension,
                        reference,
                        environment
                    )
                ),
                bridge.repositoryFor(descriptor.extension),
                this@DefaultPartitionResolver,
            )
        }

        override fun cache(
            partition: String,
            environment: String,
            parent: ExtensionParent
        ): AsyncJob<Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>> = asyncJob {
            cache(
                PartitionArtifactRequest(
                    PartitionDescriptor(
                        parent.toDescriptor(),
                        partition,
                        environment
                    )
                ),
                bridge.repositoryFor(parent.toDescriptor()),
                this@DefaultPartitionResolver
            )().merge()
        }

        // Delegation
        override val trace: ArchiveTrace by helper::trace

        override fun <D : ArtifactMetadata.Descriptor, T : ArtifactRequest<D>, R : RepositorySettings> cache(
            request: T,
            repository: R,
            resolver: ArchiveNodeResolver<D, T, *, R, *>
        ): AsyncJob<Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>> {
            return helper.cache(request, repository, resolver)
        }

        override fun <D : ArtifactMetadata.Descriptor, M : ArtifactMetadata<D, *>> cache(
            artifact: Artifact<M>,
            resolver: ArchiveNodeResolver<D, *, *, *, M>
        ): AsyncJob<Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>> {
            return helper.cache(artifact, resolver)
        }

        override fun newData(
            descriptor: PartitionDescriptor,
            parents: List<Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>>
        ): Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>> {
            return runBlocking { // Decide if it is needed to await for dependencies here or if the await should be moved to the initializer.
                val fullParents = (parents + dependencies.awaitAll()).filterDuplicates()

                helper.newData(descriptor, fullParents)
            }
        }

        override fun withResource(name: String, resource: Resource) {
            return helper.withResource(name, resource)
        }
    }
}