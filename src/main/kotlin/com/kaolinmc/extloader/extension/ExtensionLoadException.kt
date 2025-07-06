package com.kaolinmc.extloader.extension

import com.kaolinmc.extloader.exception.ExtLoaderExceptions
import com.kaolinmc.tooling.api.exception.ExceptionConfiguration
import com.kaolinmc.tooling.api.exception.StructuredException
import com.kaolinmc.tooling.api.extension.artifact.ExtensionDescriptor

public fun ExtensionLoadException(
    descriptor: ExtensionDescriptor,
    cause: Throwable? = null,
    message: String = "Error loading extension: '$descriptor'",
    configure: ExceptionConfiguration.() -> Unit = {},
): Throwable = StructuredException(ExtLoaderExceptions.ExtensionLoadException, cause, message) {
    configure()
}