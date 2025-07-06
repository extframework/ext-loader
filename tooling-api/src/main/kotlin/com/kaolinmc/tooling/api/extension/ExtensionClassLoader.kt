package com.kaolinmc.tooling.api.extension

import com.kaolinmc.boot.loader.IntegratedLoader
import com.kaolinmc.tooling.api.extension.partition.ExtensionPartitionContainer

public open class ExtensionClassLoader(
    name: String,
//    public val partitions: MutableList<ExtensionPartitionContainer<*, *>>,
    parent: ClassLoader,
) : IntegratedLoader(
    name = "Extension $name",
    parent = parent
)