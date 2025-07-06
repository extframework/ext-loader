package com.kaolinmc.tooling.api

import com.kaolinmc.boot.archive.ArchiveGraph
import com.kaolinmc.boot.monad.Tree
import com.kaolinmc.tooling.api.environment.ExtensionEnvironment
import com.kaolinmc.tooling.api.extension.ExtensionNode
import com.kaolinmc.tooling.api.extension.ExtensionResolver
import com.kaolinmc.tooling.api.extension.ExtensionRuntimeModel
import com.kaolinmc.tooling.api.extension.artifact.ExtensionDescriptor
import com.kaolinmc.tooling.api.extension.artifact.ExtensionRepositorySettings

public interface ExtensionLoader : ExtensionEnvironment.Attribute {
    public val extensionResolver: ExtensionResolver
    public val graph: ArchiveGraph
    public val environment: ExtensionEnvironment

    override val key: ExtensionEnvironment.Attribute.Key<*>
        get() = ExtensionLoader

    public companion object : ExtensionEnvironment.Attribute.Key<ExtensionLoader>

    // Environment INDEPENDENT operations
    public suspend fun cache(
        requests: Map<ExtensionDescriptor, ExtensionRepositorySettings>
    ): List<Tree<ExtensionData>>

    public suspend fun load(
        extensions: List<ExtensionDescriptor>
    ): List<ExtensionNode>

    // Environment DEPENDENT operations
    public suspend fun tweak(
        extensions: List<ExtensionNode>,
    )

//    // TODO serious thought
    public suspend fun unload(
    descriptor: ExtensionDescriptor,
    )

    public data class ExtensionData(
        val descriptor: ExtensionDescriptor,
        val model: ExtensionRuntimeModel
    )
}