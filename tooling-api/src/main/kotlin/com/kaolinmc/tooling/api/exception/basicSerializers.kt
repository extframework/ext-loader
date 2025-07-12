package com.kaolinmc.tooling.api.exception

import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
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
            serializedValues.joinToString(prefix = "[\n", postfix = "\n]", separator = "\n") {
                helper.padBy(it, 4)
            }
        } else serializedValues.joinToString(prefix = "[", postfix = "]")
    }
}

internal class MapContextSerializer : ExceptionContextSerializer<Map<Any, Any>> {
    override val type: Class<Map<Any, Any>> = getType<Map<Any, Any>>().java

    override fun serialize(value: Map<Any, Any>, helper: ExceptionContextSerializer.Helper): String {
        return value.entries.joinToString(prefix = "[", postfix = "\n]") { (key, value) ->
           helper.padBy("\n${helper.serialize(key)} -> ${helper.serialize(value)}", 4)
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

internal class PairSerializer : ExceptionContextSerializer<Pair<*, *>> {
    override val type: Class<Pair<*, *>> = Pair::class.java

    override fun serialize(value: Pair<*, *>, helper: ExceptionContextSerializer.Helper): String = buildString {
        append("(")
        append(value.first?.let(helper::serialize) ?: "null")
        append(", ")
        append(value.second?.let(helper::serialize) ?: "null")
        append(")")
    }
}

internal class RepositorySerializer : ExceptionContextSerializer<SimpleMavenRepositorySettings> {
    override val type: Class<SimpleMavenRepositorySettings> = SimpleMavenRepositorySettings::class.java

    override fun serialize(value: SimpleMavenRepositorySettings, helper: ExceptionContextSerializer.Helper): String = buildString {
        appendLine("Maven Repository(")
        appendLine("    layout = '${value.layout.name}'")
        append(")")
    }
}