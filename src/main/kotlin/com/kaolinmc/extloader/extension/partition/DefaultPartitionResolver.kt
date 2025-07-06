package com.kaolinmc.extloader.extension.partition

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.artifact.resolver.ArtifactRepository
import com.durganmcbroom.artifact.resolver.ArtifactRequest
import com.durganmcbroom.artifact.resolver.RepositoryFactory
import com.durganmcbroom.artifact.resolver.RepositorySettings
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.resources.Resource
import com.kaolinmc.archives.ArchiveReference
import com.kaolinmc.archives.Archives
import com.kaolinmc.archives.zip.ZipFinder
import com.kaolinmc.boot.archive.*
import com.kaolinmc.boot.audit.Auditors
import com.kaolinmc.boot.constraint.registerConstraintNegotiator
import com.kaolinmc.boot.monad.Either
import com.kaolinmc.boot.monad.Tagged
import com.kaolinmc.boot.monad.Tree
import com.kaolinmc.common.util.filterDuplicates
import com.kaolinmc.extloader.extension.ExtensionConstraintNegotiator
import com.kaolinmc.extloader.extension.ExtensionLoadException
import com.kaolinmc.extloader.extension.partition.artifact.PartitionArtifactRepository
import com.kaolinmc.extloader.extension.partition.artifact.PartitionRepositoryFactory
import com.kaolinmc.tooling.api.TOOLING_API_VERSION
import com.kaolinmc.tooling.api.environment.ExtensionEnvironment
import com.kaolinmc.tooling.api.environment.dependencyTypesAttrKey
import com.kaolinmc.tooling.api.environment.partitionLoadersAttrKey
import com.kaolinmc.tooling.api.extension.*
import com.kaolinmc.tooling.api.extension.artifact.ExtensionDescriptor
import com.kaolinmc.tooling.api.extension.artifact.ExtensionRepositorySettings
import com.kaolinmc.tooling.api.extension.partition.*
import com.kaolinmc.tooling.api.extension.partition.artifact.PartitionArtifactMetadata
import com.kaolinmc.tooling.api.extension.partition.artifact.PartitionArtifactRequest
import com.kaolinmc.tooling.api.extension.partition.artifact.PartitionDescriptor
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

public open class DefaultPartitionResolver(
    private val bridge: ExtensionResolver.AccessBridge,
    private val environment: ExtensionEnvironment,
) : PartitionResolver, RegisterAuditor {
    override val factory: RepositoryFactory<ExtensionRepositorySettings, ArtifactRepository<ExtensionRepositorySettings, PartitionArtifactRequest, PartitionArtifactMetadata>> = PartitionRepositoryFactory { p, settings ->
        bridge.ermFor(p.extension).partitions.find {
            it.name == p.partition
        }?.takeIf { bridge.repositoryFor(p.extension) == settings }
    }

    override val apiVersion: Int = TOOLING_API_VERSION

    override val id: String
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

    protected fun getLoader(
        prm: PartitionRuntimeModel,
    ): ExtensionPartitionLoader<ExtensionPartitionMetadata> {
        val partitionLoaders = environment[partitionLoadersAttrKey].container

        return (partitionLoaders[prm.type] as? ExtensionPartitionLoader<ExtensionPartitionMetadata>)
            ?: throw IllegalArgumentException(
                "Illegal partition type: '${prm.type}', only accepted ones are: '${
                    partitionLoaders?.map(
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
    ): ExtensionPartitionMetadata =
//        parsedMetadata[erm.descriptor to (prm.name)] ?:

    loader.parseMetadata(
        prm,
        archive,
        object : PartitionMetadataHelper {
            override val erm: ExtensionRuntimeModel = erm
        }
    )
//        .also {
//        parsedMetadata[erm.descriptor to (prm.name)] = it
//    }

    override fun load(
        data: ArchiveData<PartitionDescriptor, CachedArchiveResource>,
        accessTree: ArchiveAccessTree,
        helper: ResolutionHelper
    ): ExtensionPartitionContainer<*, *> {
        val archive = data.resources["partition.jar"]?.path?.let { Archives.find(it, ZipFinder) }
        val erm = bridge.ermFor(data.descriptor.extension)
        // Should never be null if getting to this stage.
        val prm = erm.partitions.find { it.name == data.descriptor.partition }!!

        val loader = getLoader(prm)
        val metadata = parseMetadata(loader, erm.partitions.find {
            it.name == prm.name
        } ?: throw PartitionLoadException(prm.name, "Partition not defined in the erm!") {
            erm.descriptor asContext "Extension"
        }, erm, archive)

        val parentLoader = bridge.classLoaderFor(data.descriptor.extension)

        return loader.load(
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
                ): ExtensionPartitionMetadata =
                    parsedMetadata[erm.descriptor to (partition)]
                        ?: throw ExtensionLoadException(
                            data.descriptor.extension,
                            message = "Partition loader attempting to retrieve metadata for an extension partition that has not yet been cached."
                        ) {
                            solution("Cache the requested partition in the cache method of your partition loader.")

                            prm.name asContext "Partition name"
                            partition asContext "Requested partition name"
                        }

                override fun get(name: String): CachedArchiveResource? {
                    return data.resources[name]
                }
            }
        )
    }

    override suspend fun cache(
        metadata: PartitionArtifactMetadata,
        parents: List<Tree<Either<PartitionArtifactMetadata, TaggedIArchive>>>,
        helper: CacheHelper<PartitionDescriptor>
    ): Tree<TaggedIArchive> = coroutineScope {
        val descriptor = metadata.descriptor
        val erm = bridge.ermFor(descriptor.extension)
        val prm = erm.namedPartitions[descriptor.partition]
            ?: throw PartitionLoadException(
                descriptor.partition,
                "Unknown partition: '${descriptor.partition}'. It was not defined by this extensions runtime model."
            ) {
                erm.descriptor asContext "Extension name"
            }
        val loader = getLoader(prm)
        val dependencyTypes = environment[dependencyTypesAttrKey].container

        helper.withResource("partition.jar", metadata.resource)

        val dependencies = async { cachePartitionDependencies(
            prm,
            descriptor.extension.artifact,
            dependencyTypes,
            helper
        ).awaitAll() }

        loader.cache(
            metadata,
            parents,
            DefaultPartitionCacheHelper(
                erm, prm, helper, descriptor, dependencies
            )
        )
    }

    protected inner class DefaultPartitionCacheHelper(
        override val erm: ExtensionRuntimeModel,
        override val prm: PartitionRuntimeModel,
        private val helper: CacheHelper<PartitionDescriptor>,
        private val descriptor: PartitionDescriptor,
        private val dependencies: Deferred<List<Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>>>
    ) : PartitionCacheHelper {
//        override val defaultEnvironment: String = this@DefaultPartitionResolver.defaultEnvironment

        override suspend fun cache(
            partition: String,
//            environment: String
        ): Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>> {
            return cache(
                PartitionArtifactRequest(
                    PartitionDescriptor(
                        descriptor.extension,
                        partition,
//                        environment
                    )
                ),
                bridge.repositoryFor(descriptor.extension),
                this@DefaultPartitionResolver,
            )
        }

        override suspend fun cache(
            partition: String,
//            environment: String,
            parent: ExtensionParent
        ): Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>> {
            return cache(
                PartitionArtifactRequest(
                    PartitionDescriptor(
                        parent.toDescriptor(),
                        partition,
//                        environment
                    )
                ),
                bridge.repositoryFor(parent.toDescriptor()),
                this@DefaultPartitionResolver
            )
        }

        // Delegation
        override val trace: ArchiveTrace by helper::trace

        override suspend fun <D : ArtifactMetadata.Descriptor, T : ArtifactRequest<D>, R : RepositorySettings> cache(
            request: T,
            repository: R,
            resolver: ArchiveNodeResolver<D, T, *, R, *>
        ): Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>> {
            return helper.cache(request, repository, resolver)
        }

        override suspend fun <D : ArtifactMetadata.Descriptor, M : ArtifactMetadata<D, *>> cache(
            artifact: Tree<Either<M, TaggedIArchive>>,
            resolver: ArchiveNodeResolver<D, *, *, *, M>
        ): Tree<TaggedIArchive> {
            return helper.cache(artifact, resolver)
        }

        override suspend fun newData(
            descriptor: PartitionDescriptor,
            parents: List<Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>>
        ): Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>> {
            val fullParents = (parents + dependencies.await()).filterDuplicates()

            return helper.newData(descriptor, fullParents)
        }

        override fun withResource(name: String, resource: Resource) {
            return helper.withResource(name, resource)
        }
    }
}