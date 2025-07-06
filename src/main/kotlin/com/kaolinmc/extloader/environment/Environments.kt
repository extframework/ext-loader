@file:JvmName("Environments")

package com.kaolinmc.extloader.environment

import com.kaolinmc.extloader.extension.partition.TweakerPartitionLoader
import com.kaolinmc.tooling.api.environment.ObjectContainerAttribute
import com.kaolinmc.tooling.api.extension.partition.ExtensionPartitionLoader

public fun ObjectContainerAttribute<ExtensionPartitionLoader<*>>.registerLoaders() {
    TweakerPartitionLoader().also { container.register( it) }
}