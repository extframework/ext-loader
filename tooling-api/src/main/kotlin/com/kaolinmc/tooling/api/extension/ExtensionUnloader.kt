package com.kaolinmc.tooling.api.extension

import com.kaolinmc.tooling.api.environment.ExtensionEnvironment

public interface ExtensionUnloader : ExtensionEnvironment.Attribute {
    override val key: ExtensionEnvironment.Attribute.Key<*>
        get() = ExtensionUnloader

    public fun cleanup(nodes: List<ExtensionNode>)

    public companion object : ExtensionEnvironment.Attribute.Key<ExtensionUnloader>
}