package com.kaolinmc.tooling.api.extension.partition

import com.kaolinmc.tooling.api.exception.ExceptionConfiguration
import com.kaolinmc.tooling.api.exception.InternalExceptions
import com.kaolinmc.tooling.api.exception.StructuredException

public fun PartitionLoadException(
    partition: String,
    message: String,
    cause: Throwable? = null,
    configure: ExceptionConfiguration.() -> Unit = {},
): StructuredException = StructuredException(
    InternalExceptions.PartitionLoadException,
    cause,
    "Error loading partition '$partition' because ${message.replaceFirstChar(Char::lowercase)}",
) {
    partition asContext "Partition name"
    configure()
}