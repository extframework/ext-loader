package dev.extframework.extloader

import dev.extframework.extloader.exception.handleException
import dev.extframework.tooling.api.exception.StructuredException

public fun Throwable.handleStructuredException(
): Throwable {
    return if (this !is StructuredException) {
        this
    } else {
        Formatted(
            handleException(
//                env[exceptionCxtSerializersAttrKey].extract(),
//                env[StackTracePrinter].extract(),

//                BasicExceptionPrinter(),
                this
            )
        )
    }
}

public fun <T> Result<T>.handleStructuredException(

): T {
    exceptionOrNull()?.run {
        throw handleStructuredException()
    }

    return getOrNull()!!
}