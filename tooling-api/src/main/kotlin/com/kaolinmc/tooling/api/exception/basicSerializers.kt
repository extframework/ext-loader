package com.kaolinmc.tooling.api.exception

import java.nio.file.Path
import kotlin.reflect.KClass

private inline fun <reified T : Any> getType(): KClass<T> = T::class

internal class AnyContextSerializer : ExceptionContextSerializer<Any> {
    override val type: Class<Any> = Any::class.java

    override fun serialize(value: Any, helper: ExceptionContextSerializer.Helper): String = value.toString()
}

internal class IterableContextSerializer : ExceptionContextSerializer<Iterable<Any>> {
    override val type: Class<Iterable<Any>> = getType<Iterable<Any>>().java
    override fun serialize(value: Iterable<Any>, helper: ExceptionContextSerializer.Helper): String {
        val serializedValues = value.map(helper::serialize)

        return if (serializedValues.sumOf { it.length } > 30) {
            serializedValues.joinToString(prefix = "[", postfix = "\n   ]") {
                "\n    $it"
            }
        } else serializedValues.joinToString(prefix = "[", postfix = "]")
    }
}

internal class MapContextSerializer : ExceptionContextSerializer<Map<Any, Any>> {
    override val type: Class<Map<Any, Any>> = getType<Map<Any, Any>>().java

    override fun serialize(value: Map<Any, Any>, helper: ExceptionContextSerializer.Helper): String {
        return value.entries.joinToString(prefix = "[", postfix = "\n   ]") { (key, value) ->
            "\n    ${helper.serialize(key)} -> ${helper.serialize(value)}"
        }
    }
}

internal class StringContextSerializer : ExceptionContextSerializer<String> {
    override val type: Class<String> = String::class.java

    override fun serialize(value: String, helper: ExceptionContextSerializer.Helper): String {
        return "\"$value\""
    }
}

internal class PathContextSerializer : ExceptionContextSerializer<Path> {
    override val type: Class<Path> = Path::class.java

    override fun serialize(value: Path, helper: ExceptionContextSerializer.Helper): String {
        return value.toString()
    }
}