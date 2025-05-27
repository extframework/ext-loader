package dev.extframework.extloader

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.jobs.async.AsyncJob
import com.durganmcbroom.jobs.async.asyncJob
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
import dev.extframework.tooling.api.environment.EnvironmentRegistry
import dev.extframework.tooling.api.environment.ExtensionEnvironment
import dev.extframework.tooling.api.exception.StructuredException
import dev.extframework.tooling.api.extension.*
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
    override val rootEnvironment: ExtensionEnvironment,
    override val environmentRegistry: EnvironmentRegistry,
) : ExtensionLoader {
    private val loaded: MutableList<ExtensionNode> = ArrayList()

    init {
        checkRegistration(rootEnvironment)
        rootEnvironment += this
    }

    override fun cache(
        requests: Map<ExtensionDescriptor, ExtensionRepositorySettings>,
    ): AsyncJob<List<Tree<ExtensionLoader.ExtensionData>>> = asyncJob {
        runCatching {
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

            val result = graph.cacheAsync(
                uberExtensionRequest,
                UberRepositorySettings,
                UberResolver
            )().merge()

            result.parents.map {
                it.map {
                    val value = it.value

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
        }.handleStructuredException()
    }

    override fun load(
        extensions: List<ExtensionDescriptor>
    ): AsyncJob<List<ExtensionNode>> = asyncJob {
        runCatching {
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
                return@asyncJob listOf()
            } else if (possibleSupers.size > 1) {
                throw StructuredException(
                    ExtLoaderExceptions.ExtensionLoadException,
                    message = "Illegal state loading extensions. The extension group: '$extensions' must all have been cached together."
                )
            } else {
                possibleSupers.first()
            }

            val extensions = graph.get(
                descriptor,
                UberResolver
            )().merge()
                .buildTree()
                .toList()
                .filterIsInstance<ExtensionNode>()

            loaded.clear()
            loaded.addAll(extensions)

            extensions
        }.handleStructuredException()
    }

    override fun tweak(
        extensions: List<ExtensionNode>,
        environment: ExtensionEnvironment
    ): AsyncJob<Unit> = asyncJob {
        runCatching {
            checkRegistration(environment)

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
                            rootEnvironment.name
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

            graph.cacheAsync(
                uberTweakerRequest,
                UberRepositorySettings,
                UberResolver
            )().merge()

            val uberTweakers = graph.get(
                uberDescriptor,
                UberResolver
            )().merge()

            val tweakers = extensions
                .mapNotNull { archive ->
                    uberTweakers.access
                        .targets
                        .map { target -> target.relationship.node }
                        .filterIsInstance<ExtensionPartitionContainer<TweakerPartitionNode, *>>()
                        .find { container -> container.descriptor.extension == archive.descriptor }
                }
                .reversed()
                .filterDuplicates()

            val tweaked: MutableSet<ExtensionDescriptor> = HashSet()

            tweakers.forEach {
                if (tweaked.add(it.descriptor.extension)) {
                    it.node.tweaker.tweak(environment)().merge()
                }
            }
        }.handleStructuredException()
    }

    // TODO This needs some serious thought put into it.
    override fun unload(
        descriptor: ExtensionDescriptor,
    ): AsyncJob<Unit> = asyncJob {
        runCatching {
            val node = loaded.find { it.descriptor == descriptor }
            if (node != null) {
                val reloadable = node.runtimeModel.attributes[UNLOADABLE_ATTR_KEY] != "false"

                if (!reloadable) {
                    throw StructuredException(
                        ExtLoaderExceptions.ExtensionNotUnloadable,
                        message = "This extension is not unloadable!"
                    ) {
                        descriptor asContext "Extension descriptor"
                    }
                }
            } else {
                return@asyncJob
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
            }().merge() ?: return@asyncJob

            val unloaded = graph
                .unload(toUnload)().merge()
                .filterIsInstance<ExtensionNode>()

            loaded.removeAll(unloaded)

            val environments = HashSet<String>()

            // Unloading partitions
            for (extensionNode in unloaded) {
                val partitions = graph.nodes()
                    .map { it.descriptor }
                    .filterIsInstance<PartitionDescriptor>()
                    .filter {
                        it.extension == extensionNode.descriptor
                    }

                for (descriptor in partitions) {
                    environments.add(descriptor.environment)

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
                    }().merge() ?: descriptor

                    graph.unload(
                        unloadablePartition
                    )().merge()
                }
            }

            for (name in environments) {
                val environment = environmentRegistry.get(name)!!

                environment[ExtensionUnloader]?.cleanup(
                    unloaded
                )?.invoke()?.merge()
            }
        }.handleStructuredException()
    }

//    override fun compose(): ExtensionLoader {
//        val env = environment.compose()
//
//        val newGraph = ChildDefaultArchiveGraph(graph)
//        val newPartitionResolver = DefaultPartitionResolver(
//            extensionResolver.accessBridge,
//            env
//        )
//        newGraph.registerResolver(newPartitionResolver)
//
//        return DefaultExtensionLoader(
//            extensionResolver,
//            newPartitionResolver,
//            newGraph,
//            env,
//            this
//        )
//    }

    private fun buildUnloadableUberChild(
        child: ArtifactMetadata.Descriptor,
        unloadable: (ArchiveNode<*>) -> Boolean = { true },
        requestBuilder: (ArtifactMetadata.Descriptor) -> UberParentRequest<*, *, *>?
    ): AsyncJob<UberDescriptor?> = asyncJob {
        val uber = UberResolver.by[child] ?: return@asyncJob null

        val targets = (graph.getNode(uber) ?: return@asyncJob null)
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
            )().merge()

            graph.get(
                descriptor,
                UberResolver
            )().merge()
        }

        uber
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

    protected fun checkRegistration(
        environment: ExtensionEnvironment,
    ) {
        if (!environmentRegistry.has(environment.name)) {
            environmentRegistry.register(environment.name, environment)
        }
    }
}