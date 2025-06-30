package dev.extframework.extloader

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import dev.extframework.boot.archive.*
import dev.extframework.boot.audit.Auditors
import dev.extframework.boot.monad.Tagged
import dev.extframework.extloader.extension.DefaultExtensionResolver
import dev.extframework.extloader.extension.ExtensionLayerClassLoader
import dev.extframework.`object`.ObjectContainer
import dev.extframework.tooling.api.environment.ExtensionEnvironment
import dev.extframework.tooling.api.environment.MapView
import dev.extframework.tooling.api.environment.ObjectContainerView
import dev.extframework.tooling.api.extension.ExtensionClassLoader
import dev.extframework.tooling.api.extension.ExtensionResolver
import dev.extframework.tooling.api.extension.ExtensionRuntimeModel
import dev.extframework.tooling.api.extension.artifact.ExtensionDescriptor
import dev.extframework.tooling.api.extension.artifact.ExtensionRepositorySettings
import java.nio.file.Path

public open class ArchiveGraphView(
    private val _reference: () -> ArchiveGraph,
) : DefaultArchiveGraph(_reference().path) {
    protected val reference: ArchiveGraph
        get() = _reference()

    override val path: Path = reference.path

    private var _auditors: Auditors = Auditors()
    override var auditors: Auditors
        get() = reference.auditors.chainAll(_auditors)
        set(value) {
            val map = value.auditors.mapValues { (cls, list) ->
                list.filterNot {
                    reference.auditors.auditors[cls]?.contains(it) == true
                }
            }.filter {
                it.value.isNotEmpty()
            }

            _auditors = Auditors(map)
        }

    override val nodes: MutableMap<
            ArtifactMetadata.Descriptor,
            Tagged<ArchiveNode<*>, ArchiveNodeResolver<*, *, *, *, *>>
            > = MapView {
        reference.nodes
    }

    override val resolvers: ObjectContainer<ArchiveNodeResolver<*, *, *, *, *>> =
        object : ObjectContainerView<ArchiveNodeResolver<*, *, *, *, *>>({
            reference.resolvers
        }) {
            override fun register(obj: ArchiveNodeResolver<*, *, *, *, *>): Boolean {
                if (obj is RegisterAuditor) {
                    auditors = obj.register(auditors)
                }
                return super.register(obj)
            }
        }
}

public open class ExtensionResolverView(
    private val _reference: () -> ExtensionResolver,
    environment: ExtensionEnvironment,
) : DefaultExtensionResolver(
    ClassLoader.getSystemClassLoader(),
    environment
) {
    protected val reference: ExtensionResolver
        get() = _reference()
    override val layerLoader: ExtensionLayerClassLoader = ExtensionLayerClassLoader(
        reference.layerLoader,
        "Extension Layer ${environment.name}"
    )

    override val accessBridge: ExtensionResolver.AccessBridge = object : ExtensionResolver.AccessBridge {
        override fun classLoaderFor(descriptor: ExtensionDescriptor): ExtensionClassLoader {
            return (extensionClassloaders[descriptor.toIdentifier()]) ?: reference.accessBridge.classLoaderFor(
                descriptor
            )
        }

        override fun ermFor(descriptor: ExtensionDescriptor): ExtensionRuntimeModel {
            return extensionMetadata[descriptor.toIdentifier()]?.erm ?: reference.accessBridge.ermFor(descriptor)
        }

        override fun repositoryFor(descriptor: ExtensionDescriptor): ExtensionRepositorySettings {
            return extensionMetadata[descriptor.toIdentifier()]?.repository ?: reference.accessBridge.repositoryFor(
                descriptor
            )
        }
    }
}