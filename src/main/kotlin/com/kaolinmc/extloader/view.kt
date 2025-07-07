package com.kaolinmc.extloader

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.kaolinmc.boot.archive.*
import com.kaolinmc.boot.audit.Auditors
import com.kaolinmc.boot.monad.Tagged
import com.kaolinmc.extloader.extension.DefaultExtensionResolver
import com.kaolinmc.extloader.extension.ExtensionLayerClassLoader
import com.kaolinmc.`object`.ObjectContainer
import com.kaolinmc.tooling.api.environment.ExtensionEnvironment
import com.kaolinmc.tooling.api.environment.MapView
import com.kaolinmc.tooling.api.environment.ObjectContainerView
import com.kaolinmc.tooling.api.extension.ExtensionClassLoader
import com.kaolinmc.tooling.api.extension.ExtensionResolver
import com.kaolinmc.tooling.api.extension.ExtensionRuntimeModel
import com.kaolinmc.tooling.api.extension.artifact.ExtensionDescriptor
import com.kaolinmc.tooling.api.extension.artifact.ExtensionRepositorySettings
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
            return (extensionClassloaders[descriptor]) ?: reference.accessBridge.classLoaderFor(
                descriptor
            )
        }

        override fun ermFor(descriptor: ExtensionDescriptor): ExtensionRuntimeModel {
            return extensionMetadata[descriptor]?.erm ?: reference.accessBridge.ermFor(descriptor)
        }

        override fun repositoryFor(descriptor: ExtensionDescriptor): ExtensionRepositorySettings {
            return extensionMetadata[descriptor]?.repository ?: reference.accessBridge.repositoryFor(
                descriptor
            )
        }
    }
}