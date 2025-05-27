@file:JvmName("Environments")

package dev.extframework.extloader.environment

import dev.extframework.extloader.exception.*
import dev.extframework.extloader.extension.partition.TweakerPartitionLoader
import dev.extframework.tooling.api.environment.*
import dev.extframework.tooling.api.exception.ExceptionContextSerializer
import dev.extframework.tooling.api.extension.partition.ExtensionPartitionLoader

public fun MutableObjectContainerAttribute<ExtensionPartitionLoader<*>>.registerLoaders() {
    TweakerPartitionLoader().also { container.register(it.type, it) }
}

internal fun MutableObjectSetAttribute<ExceptionContextSerializer<*>>.registerBasicSerializers(): MutableObjectSetAttribute<ExceptionContextSerializer<*>> {
    AnyContextSerializer().also(::add)
    IterableContextSerializer().also(::add)
    MapContextSerializer().also(::add)
    StringContextSerializer().also(::add)
    PathContextSerializer().also(::add)

    return this
}