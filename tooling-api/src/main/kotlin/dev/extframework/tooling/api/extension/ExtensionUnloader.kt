package dev.extframework.tooling.api.extension

import com.durganmcbroom.jobs.Job
import dev.extframework.tooling.api.environment.ExtensionEnvironment

public interface ExtensionUnloader : ExtensionEnvironment.Attribute {
    override val key: ExtensionEnvironment.Attribute.Key<*>
        get() = ExtensionUnloader

    public fun cleanup(nodes: List<ExtensionNode>) : Job<Unit>

    public companion object : ExtensionEnvironment.Attribute.Key<ExtensionUnloader>
}