@file:JvmName("Environments")

package dev.extframework.extloader.environment

import dev.extframework.extloader.extension.partition.TweakerPartitionLoader
import dev.extframework.tooling.api.environment.MutableObjectContainerAttribute
import dev.extframework.tooling.api.extension.partition.ExtensionPartitionLoader

public fun MutableObjectContainerAttribute<ExtensionPartitionLoader<*>>.registerLoaders() {
    TweakerPartitionLoader().also { container.register(it.type, it) }
}