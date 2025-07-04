package dev.extframework.extloader

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.fasterxml.jackson.module.kotlin.readValue
import dev.extframework.boot.archive.*
import dev.extframework.boot.monad.Tree
import dev.extframework.boot.monad.map
import dev.extframework.boot.monad.toList
import dev.extframework.boot.util.basicObjectMapper
import dev.extframework.common.util.filterDuplicates
import dev.extframework.extloader.exception.ExtLoaderExceptions
import dev.extframework.extloader.extension.ExtensionLoadException
import dev.extframework.extloader.extension.partition.TweakerPartitionNode
import dev.extframework.tooling.api.ExtensionLoader
import dev.extframework.tooling.api.environment.ExtensionEnvironment
import dev.extframework.tooling.api.exception.StructuredException
import dev.extframework.tooling.api.extension.ExtensionNode
import dev.extframework.tooling.api.extension.ExtensionResolver
import dev.extframework.tooling.api.extension.ExtensionRuntimeModel
import dev.extframework.tooling.api.extension.ExtensionUnloader
import dev.extframework.tooling.api.extension.artifact.ExtensionArtifactRequest
import dev.extframework.tooling.api.extension.artifact.ExtensionDescriptor
import dev.extframework.tooling.api.extension.artifact.ExtensionRepositorySettings
import dev.extframework.tooling.api.extension.partition.ExtensionPartitionContainer
import dev.extframework.tooling.api.extension.partition.artifact.PartitionArtifactRequest
import dev.extframework.tooling.api.extension.partition.artifact.PartitionDescriptor
import dev.extframework.tooling.api.uber.*
import java.nio.file.Files

public open class DefaultExtensionLoader(
    override val extensionResolver: ExtensionResolver,
    override val graph: ArchiveGraph,
    override val environment: ExtensionEnvironment
) : ExtensionLoader {
    protected open val loaded: MutableList<ExtensionNode> = ArrayList()

    override suspend fun cache(
        requests: Map<ExtensionDescriptor, ExtensionRepositorySettings>,
    ): List<Tree<ExtensionLoader.ExtensionData>> {
        val uber = UberDescriptor("All Extensions")
        val uberExtensionRequest = UberArtifactRequest(
            uber,
            (requests
                .filterNot { loaded.any { n -> n.descriptor == it.key } }
                .map {
                    UberParentRequest(
                        ExtensionArtifactRequest(it.key), it.value, extensionResolver
                    )
                } + loaded.map {
                UberParentRequest(
                    ExtensionArtifactRequest(
                        it.descriptor
                    ),
                    extensionResolver.accessBridge.repositoryFor(it.descriptor),
                    extensionResolver
                )
            }).filterDuplicates()
        )

        val result = try {
            graph.cache(
                uberExtensionRequest,
                UberRepositorySettings,
                UberResolver
            )
        } catch (e: ArchiveException.ArchiveNotFound) {
            throw StructuredException(
                ExtLoaderExceptions.ExtensionNotFound,
                e,
                "Failed to find extension: '${e.archive}'"
            ) {
                e.lookedIn asContext "Repositories Searched"
                solution("Make sure the descriptor is typed correctly and the repository is defined correctly.")
            }
        } catch (e: Throwable) {
            throw StructuredException(
                ExtLoaderExceptions.ExtensionCacheException,
                e,
                "An unexpected error occurred when attempting to cache."
            ) {
                solution("Check your environment and continue.")
                requests asContext "Extension requests"
            }
        }

        return result.parents.map {
            it.map { t ->
                val value = t.value

                ExtensionLoader.ExtensionData(
                    value.descriptor as ExtensionDescriptor,
                    when (value) {
                        is ArchiveData<*, *> -> {
                            val resource = value.resources["erm.json"]!! as CachedArchiveResource
                            resource.path.let {
                                basicObjectMapper.readValue<ExtensionRuntimeModel>(
                                    Files.readAllBytes(it)
                                )
                            }
                        }

                        is ExtensionNode -> {
                            value.runtimeModel
                        }

                        else -> throw Exception("This should not happen")
                    }
                )
            }
        }
    }

    override suspend fun load(
        extensions: List<ExtensionDescriptor>
    ): List<ExtensionNode> {
        val possibleSupers = extensions
            .filterNot { d -> loaded.any { it.descriptor == d } }
            .map {
                UberResolver.by[it] ?: throw ExtensionLoadException(
                    it,
                    message = "The extension: '$it' has not been cached in this instance!"
                )
            }
            .toSet()

        val descriptor = if (possibleSupers.isEmpty()) {
            return listOf()
        } else if (possibleSupers.size > 1) {
            throw StructuredException(
                ExtLoaderExceptions.ExtensionLoadException,
                description = "Illegal state loading extensions. The extension group: '$extensions' must all have been cached together."
            )
        } else {
            possibleSupers.first()
        }

        val tree = graph.get(
            descriptor,
            UberResolver
        )

        val extensions = tree
            .buildTree()
            .toList()
            .filterDuplicates()
            .filterIsInstance<ExtensionNode>()

        loaded.addAll(extensions.filterNot {
            loaded.any { n -> n.descriptor == it.descriptor }
        })

        return extensions
    }

    override suspend fun tweak(
        extensions: List<ExtensionNode>,
    ) {
//        checkRegistration(environment)

        val uberTweakerParents = extensions
            .filter { archive ->
                val erm = extensionResolver.accessBridge.ermFor(archive.descriptor)
                erm.partitions.any { model -> model.name == "tweaker" }
            }
            .map { archive ->
                UberParentRequest(
                    PartitionArtifactRequest(
                        archive.descriptor,
                        "tweaker",
                    ),
                    extensionResolver.accessBridge.repositoryFor(archive.descriptor),
                    extensionResolver.partitionResolver
                )
            }

        val uberDescriptor = UberDescriptor("All Tweakers")
        val uberTweakerRequest = UberArtifactRequest(
            uberDescriptor,
            uberTweakerParents,
        )

        graph.cache(
            uberTweakerRequest,
            UberRepositorySettings,
            UberResolver
        )

        val uberTweakers = graph.get(
            uberDescriptor,
            UberResolver
        )

        val tweakers = extensions
            .mapNotNull { archive ->
                uberTweakers.access
                    .targets
                    .map { target -> target.relationship.node }
                    .filterIsInstance<ExtensionPartitionContainer<TweakerPartitionNode, *>>()
                    .find { container -> container.descriptor.extension == archive.descriptor }
            }

        val tweaked: MutableSet<ExtensionDescriptor> = HashSet()

        tweakers.forEach {
            if (tweaked.add(it.descriptor.extension)) {
                it.node.tweaker.tweak(environment)
            }
        }
    }

    override suspend fun unload(
        descriptor: ExtensionDescriptor,
    ) {
        val node = loaded.find { it.descriptor == descriptor }
        if (node != null) {
            val reloadable = node.runtimeModel.attributes[UNLOADABLE_ATTR_KEY] != "false"

            if (!reloadable) {
                throw StructuredException(
                    ExtLoaderExceptions.ExtensionNotUnloadable,
                    description = "This extension is not unloadable!"
                ) {
                    descriptor asContext "Extension descriptor"
                }
            }
        } else {
            return
        }

        val toUnload = buildUnloadableUberChild(
            descriptor,
            {
                (it as? ExtensionNode)?.let { it.runtimeModel.attributes[UNLOADABLE_ATTR_KEY] != "false" } != false
            }
        ) {
            if (it !is ExtensionDescriptor) null
            else {
                UberParentRequest(
                    ExtensionArtifactRequest(it),
                    ExtensionRepositorySettings.local(),
                    extensionResolver
                )
            }
        } ?: return

        val unloaded = graph
            .unload(toUnload)
            .filterIsInstance<ExtensionNode>()

        loaded.removeAll(unloaded)

        environment.find(ExtensionUnloader)?.cleanup(
            unloaded
        )

        // Unloading partitions
        for (extensionNode in unloaded) {
            val partitions = graph.nodes
                .map { it.value.value.descriptor }
                .filterIsInstance<PartitionDescriptor>()
                .filter {
                    it.extension == extensionNode.descriptor
                }

            for (descriptor in partitions) {
                // Assume that everything in here is unloadable
                val unloadablePartition = buildUnloadableUberChild(
                    descriptor,
                ) {
                    if (it !is PartitionDescriptor) null
                    else {
                        UberParentRequest(
                            PartitionArtifactRequest(it),
                            ExtensionRepositorySettings.local(),
                            extensionResolver.partitionResolver
                        )
                    }
                } ?: descriptor

                graph.unload(
                    unloadablePartition
                )
            }
        }
    }

    private suspend fun buildUnloadableUberChild(
        child: ArtifactMetadata.Descriptor,
        unloadable: (ArchiveNode<*>) -> Boolean = { true },
        requestBuilder: (ArtifactMetadata.Descriptor) -> UberParentRequest<*, *, *>?
    ): UberDescriptor? {
        val uber = UberResolver.by[child] ?: return null

        val targets = (graph.nodes[uber] ?: return null)
            .value
            .access
            .targets
            .map { it.relationship }

        val toKeep = targets
            .filterIsInstance<ArchiveRelationship.Direct>()
            .map { it.node.descriptor }
            .toMutableSet()
            .apply { remove(child) }
            .apply {
                targets
                    .map { it.node }
                    .filter { !unloadable(it) }
                    .forEach { add(it.descriptor) }
            }

        if (!toKeep.isEmpty()) {
            toKeep.forEach {
                UberResolver.by.remove(it)
            }

            val descriptor = UberDescriptor("${uber.name} unloading delta")

            graph.cache(
                UberArtifactRequest(
                    descriptor,
                    toKeep.mapNotNull { requestBuilder(it) }
                ),
                UberRepositorySettings,
                UberResolver
            )

            graph.get(
                descriptor,
                UberResolver
            )
        }

        return uber
    }

    private fun ArchiveNode<*>.buildTree(): Tree<ArchiveNode<*>> {
        val parents = access.targets
            .filter { it.relationship is ArchiveRelationship.Direct }
            .map { it.relationship.node }

        return Tree(
            this,
            parents.map {
                it.buildTree()
            }
        )
    }

    override fun compose(
        into: ExtensionEnvironment
    ): ExtensionEnvironment.Attribute.View<*> {
        return View(this, into)
    }

    private class View(
        override var reference: ExtensionLoader,
        environment: ExtensionEnvironment
    ) : ExtensionEnvironment.Attribute.View<ExtensionLoader>, DefaultExtensionLoader(
        ExtensionResolverView(
            { reference.extensionResolver },
            environment
        ),
        ArchiveGraphView { reference.graph },
        environment
    ) {
        override var isValid: Boolean = true
        override val key: ExtensionEnvironment.Attribute.Key<*> = ExtensionLoader
    }

    public companion object {
        public const val UNLOADABLE_ATTR_KEY: String = "unloadable"
    }
}