package dev.extframework.tooling.api.extension

import dev.extframework.tooling.api.environment.ExtensionEnvironment

public interface ExtensionUnloader : ExtensionEnvironment.Attribute {
    override val key: ExtensionEnvironment.Attribute.Key<*>
        get() = ExtensionUnloader

    public fun cleanup(nodes: List<ExtensionNode>)

    public companion object : ExtensionEnvironment.Attribute.Key<ExtensionUnloader>
}