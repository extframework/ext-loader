package dev.extframework.extloader.extension

import com.durganmcbroom.artifact.resolver.Artifact
import com.durganmcbroom.artifact.resolver.ResolutionContext
import com.durganmcbroom.artifact.resolver.createContext
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.artifact.resolver.simple.maven.layout.SimpleMavenDefaultLayout
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.JobScope
import com.durganmcbroom.jobs.async.AsyncJob
import com.durganmcbroom.jobs.async.asyncJob
import com.durganmcbroom.jobs.async.mapAsync
import com.durganmcbroom.jobs.job
import com.durganmcbroom.jobs.mapException
import com.durganmcbroom.resources.Resource
import com.durganmcbroom.resources.toByteArray
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import dev.extframework.boot.archive.*
import dev.extframework.boot.audit.Auditors
import dev.extframework.boot.constraint.registerConstraintNegotiator
import dev.extframework.boot.monad.Tagged
import dev.extframework.boot.monad.Tree
import dev.extframework.boot.util.basicObjectMapper
import dev.extframework.extloader.extension.artifact.ExtensionArtifactRepository.Companion.parseSettings
import dev.extframework.extloader.extension.artifact.ExtensionRepositoryFactory
import dev.extframework.extloader.extension.partition.DefaultPartitionResolver
import dev.extframework.tooling.api.TOOLING_API_VERSION
import dev.extframework.tooling.api.environment.EnvironmentRegistry
import dev.extframework.tooling.api.extension.ExtensionClassLoader
import dev.extframework.tooling.api.extension.ExtensionNode
import dev.extframework.tooling.api.extension.ExtensionResolver
import dev.extframework.tooling.api.extension.ExtensionRuntimeModel
import dev.extframework.tooling.api.extension.artifact.ExtensionArtifactMetadata
import dev.extframework.tooling.api.extension.artifact.ExtensionArtifactRequest
import dev.extframework.tooling.api.extension.artifact.ExtensionDescriptor
import dev.extframework.tooling.api.extension.artifact.ExtensionRepositorySettings
import dev.extframework.tooling.api.extension.partition.PartitionResolver
import kotlinx.coroutines.awaitAll
import java.io.ByteArrayInputStream
import java.nio.file.Files

public open class DefaultExtensionResolver(
    parent: ClassLoader,
    environmentRegistry: EnvironmentRegistry,
    defaultEnvironment: String
) : ExtensionResolver, RegisterAuditor {
    private val layerLoader = ExtensionLayerClassLoader(parent)
    protected val factory: ExtensionRepositoryFactory = ExtensionRepositoryFactory()

    private val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

    private data class ExtensionMetadata(
        val erm: ExtensionRuntimeModel,
        val repository: ExtensionRepositorySettings,
    )

    // TODO determine if it is necessary for these keys to be strings instead of ExtensionDescriptors
    //   (and then additionally match on version)
    private val extensionMetadata: MutableMap<String, ExtensionMetadata> = HashMap()
    private val extensionClassloaders: MutableMap<String, ExtensionClassLoader> = HashMap()

    override val apiVersion: Int = TOOLING_API_VERSION
    override val context: ResolutionContext<ExtensionRepositorySettings, ExtensionArtifactRequest, ExtensionArtifactMetadata>
        get() = factory.createContext()

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
            return (extensionClassloaders[descriptor.toIdentifier()]) ?: extensionNotPresent(descriptor)
        }

        override fun ermFor(descriptor: ExtensionDescriptor): ExtensionRuntimeModel {
            return extensionMetadata[descriptor.toIdentifier()]?.erm ?: extensionNotPresent(descriptor)
        }

        override fun repositoryFor(descriptor: ExtensionDescriptor): ExtensionRepositorySettings {
            return extensionMetadata[descriptor.toIdentifier()]?.repository ?: extensionNotPresent(descriptor)
        }
    }
    override val partitionResolver: PartitionResolver = DefaultPartitionResolver(
        accessBridge, environmentRegistry, defaultEnvironment
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
    ): Job<ExtensionNode> = job {
        val erm = data.resources["erm.json"]!!.path.let {
            mapper.readValue<ExtensionRuntimeModel>(
                Files.readAllBytes(it)
            )
        }

        val rawRepository = data.resources["repository.json"]!!
            .path
            .toFile()
            .inputStream()
            .let { basicObjectMapper.readValue<Map<String, String>>(it) }

        val repository = parseSettings(rawRepository) as ExtensionRepositorySettings

        val cl = ExtensionClassLoader(
            data.descriptor.name,
            layerLoader
        )

        extensionMetadata[data.descriptor.toIdentifier()] = ExtensionMetadata(
            erm,
            repository,
        )
        extensionClassloaders[data.descriptor.toIdentifier()] = cl

        val parents = accessTree.targets
            .map(ArchiveTarget::relationship)
            .filterIsInstance<ArchiveRelationship.Direct>()
            .map(ArchiveRelationship.Direct::node)
            .filterIsInstance<ExtensionNode>()

        ExtensionNode(
            data.descriptor,
            accessTree,
            parents,
            cl,
            erm
        )
    }

    override fun cache(
        artifact: Artifact<ExtensionArtifactMetadata>,
        helper: CacheHelper<ExtensionDescriptor>
    ): AsyncJob<Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>> = asyncJob {
        helper.withResource(
            "erm.json",
            artifact.metadata.erm,
        )

        helper.withResource(
            "repository.json",
            writeRepositoryToResource(artifact.metadata)
        )

        extensionMetadata[artifact.metadata.descriptor.toIdentifier()] = ExtensionMetadata(
            mapper.readValue<ExtensionRuntimeModel>(artifact.metadata.erm.open().toByteArray()),
            artifact.metadata.repository,
        )

        val parents = artifact.parents.mapAsync {
            helper.cache(
                it,
                this@DefaultExtensionResolver
            )().merge()
        }

        helper.newData(
            artifact.metadata.descriptor,
            parents
                .onEach { it.start() }
                .awaitAll()
        )
    }

    private fun JobScope.writeRepositoryToResource(metadata: ExtensionArtifactMetadata): Resource = Resource("<heap>") {
        val repository = metadata.repository

        runCatching {
            ByteArrayInputStream(
                basicObjectMapper.writeValueAsBytes(
                    mapOf(
                        "location" to repository.layout.location,
                        "preferredHash" to repository.preferredHash.name,
                        "type" to if (repository.layout is SimpleMavenDefaultLayout) "default" else "local"
                    )
                )
            )
        }.mapException {
            ExtensionLoadException(metadata.descriptor, it) {
                metadata.descriptor asContext "Extension name"
            }
        }.merge()
    }

    private fun ExtensionDescriptor.toIdentifier(): String {
        return "$group:$artifact"
    }
}
