package com.kaolinmc.extloader.extension

import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.artifact.resolver.simple.maven.layout.SimpleMavenDefaultLayout
import com.durganmcbroom.resources.toByteArray
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.kaolinmc.boot.archive.*
import com.kaolinmc.boot.audit.Auditors
import com.kaolinmc.boot.constraint.registerConstraintNegotiator
import com.kaolinmc.boot.monad.Either
import com.kaolinmc.boot.monad.Tree
import com.kaolinmc.boot.util.mapAsync
import com.kaolinmc.common.util.make
import com.kaolinmc.common.util.resolve
import com.kaolinmc.extloader.extension.artifact.ExtensionArtifactRepository.Companion.parseSettings
import com.kaolinmc.extloader.extension.artifact.ExtensionRepositoryFactory
import com.kaolinmc.extloader.extension.partition.DefaultPartitionResolver
import com.kaolinmc.tooling.api.ExtensionLoader
import com.kaolinmc.tooling.api.TOOLING_API_VERSION
import com.kaolinmc.tooling.api.environment.ExtensionEnvironment
import com.kaolinmc.tooling.api.extension.ExtensionClassLoader
import com.kaolinmc.tooling.api.extension.ExtensionNode
import com.kaolinmc.tooling.api.extension.ExtensionResolver
import com.kaolinmc.tooling.api.extension.ExtensionRuntimeModel
import com.kaolinmc.tooling.api.extension.artifact.ExtensionArtifactMetadata
import com.kaolinmc.tooling.api.extension.artifact.ExtensionDescriptor
import com.kaolinmc.tooling.api.extension.artifact.ExtensionRepositorySettings
import com.kaolinmc.tooling.api.extension.partition.PartitionResolver
import kotlinx.coroutines.awaitAll
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeBytes

public open class DefaultExtensionResolver(
    parent: ClassLoader,
    environment: ExtensionEnvironment
) : ExtensionResolver, RegisterAuditor {
    override val layerLoader: ExtensionLayerClassLoader = ExtensionLayerClassLoader(parent)
    override val factory: ExtensionRepositoryFactory = ExtensionRepositoryFactory()
    protected val metadataPath: Path by lazy { environment[ExtensionLoader].graph.path resolve ".extension-metadata.json" }

    private val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

    protected class ExtensionMetadata(
        public val erm: ExtensionRuntimeModel,
        public val rawRepository: Map<String, String>,
    ) {
        @JsonIgnore
        public val repository: ExtensionRepositorySettings = parseSettings(rawRepository) as ExtensionRepositorySettings
    }

    protected val extensionMetadata: MutableMap<ExtensionDescriptor, ExtensionMetadata> by lazy {
        val src = metadataPath.toFile()

        if (src.exists()) {
            mapper.readValue<Map<String, ExtensionMetadata>>(
                src
            ).mapKeysTo(HashMap()) { ExtensionDescriptor.parseDescriptor(it.key) }
        } else {
            metadataPath.make()
            metadataPath.writeBytes(mapper.writeValueAsBytes(HashMap<String, ExtensionMetadata>()))
            HashMap()
        }
    }

    protected val extensionClassloaders: MutableMap<ExtensionDescriptor, ExtensionClassLoader> = HashMap()

    override val apiVersion: Int = TOOLING_API_VERSION

    override val accessBridge: ExtensionResolver.AccessBridge = object : ExtensionResolver.AccessBridge {
        private fun extensionNotPresent(descriptor: ExtensionDescriptor): Nothing {
            throw ExtensionLoadException(
                descriptor,
                message = "Failed to load a partition because this extension was not loaded yet."
            ) {
                solution("Loading the extension tree before loading partitions.")
            }
        }

        override fun classLoaderFor(descriptor: ExtensionDescriptor): ExtensionClassLoader {
            return (extensionClassloaders[descriptor]) ?: extensionNotPresent(descriptor)
        }

        override fun ermFor(descriptor: ExtensionDescriptor): ExtensionRuntimeModel {
            return extensionMetadata[descriptor]?.erm ?: extensionNotPresent(descriptor)
        }

        override fun repositoryFor(descriptor: ExtensionDescriptor): ExtensionRepositorySettings {
            return extensionMetadata[descriptor]?.repository ?: extensionNotPresent(descriptor)
        }
    }
    override val partitionResolver: PartitionResolver = DefaultPartitionResolver(
        accessBridge,
        environment
    )

    override fun register(auditors: Auditors): Auditors {
        return auditors.registerConstraintNegotiator(
            ExtensionConstraintNegotiator(
                ExtensionDescriptor::class.java, {
                    "${it.group}:${it.artifact}"
                }
            ) {
                SimpleMavenDescriptor(it.group, it.artifact, it.version, null)
            })
    }

    override fun load(
        data: ArchiveData<ExtensionDescriptor, CachedArchiveResource>,
        accessTree: ArchiveAccessTree,
        helper: ResolutionHelper
    ): ExtensionNode {
        val erm = data.resources["erm.json"]!!.path.let {
            mapper.readValue<ExtensionRuntimeModel>(
                Files.readAllBytes(it)
            )
        }

        val cl = ExtensionClassLoader(
            data.descriptor.name,
            layerLoader
        )

        extensionClassloaders[data.descriptor] = cl

        val parents = accessTree.targets
            .map(ArchiveTarget::relationship)
            .filterIsInstance<ArchiveRelationship.Direct>()
            .map(ArchiveRelationship.Direct::node)
            .filterIsInstance<ExtensionNode>()

        return ExtensionNode(
            data.descriptor,
            accessTree,
            parents,
            cl,
            erm
        )
    }

    override suspend fun cache(
        metadata: ExtensionArtifactMetadata,
        parents: List<Tree<Either<ExtensionArtifactMetadata, TaggedIArchive>>>,
        helper: CacheHelper<ExtensionDescriptor>
    ): Tree<TaggedIArchive> {
        helper.withResource(
            "erm.json",
            metadata.erm,
        )

        extensionMetadata[metadata.descriptor] = ExtensionMetadata(
            mapper.readValue<ExtensionRuntimeModel>(metadata.erm.open().toByteArray()),
            writeRepositoryToResource(metadata),
        )

        metadataPath.writeBytes(mapper.writeValueAsBytes(extensionMetadata.mapKeys {
            it.key.name
        }))

        val parents = parents.mapAsync {
            helper.cache(
                it,
                this@DefaultExtensionResolver
            )
        }

        return helper.newData(
            metadata.descriptor,
            parents
                .onEach { it.start() }
                .awaitAll()
        )
    }

    private fun writeRepositoryToResource(metadata: ExtensionArtifactMetadata): Map<String, String> {
        val repository = metadata.repository

        return try {
            mapOf(
                "location" to repository.layout.location,
                "preferredHash" to repository.preferredHash.name,
                "type" to if (repository.layout is SimpleMavenDefaultLayout) "default" else "local"
            )
        } catch (e: Throwable) {
            throw ExtensionLoadException(metadata.descriptor, e) {
                metadata.descriptor asContext "Extension name"
            }
        }
    }

    protected fun ExtensionDescriptor.toIdentifier(): String {
        return "$group:$artifact"
    }
}
