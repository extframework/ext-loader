package dev.extframework.extloader.exception

import dev.extframework.tooling.api.exception.ExceptionContextSerializer
import dev.extframework.tooling.api.exception.StackTracePrinter
import dev.extframework.tooling.api.exception.StructuredException
import java.awt.BasicStroke
import java.io.OutputStream
import java.io.PrintWriter
import java.lang.StringBuilder

internal open class HierarchicalDistance(
    val distance: Int
) {
    fun increment(): HierarchicalDistance {
        return incrementBy(1)
    }

    open fun incrementBy(int: Int) : HierarchicalDistance {
        return if (distance == Int.MAX_VALUE) this
        else if (int == Int.MAX_VALUE) NonConvergingHierarchicalDistance()
        else if (int.toLong() + distance.toLong() >= Int.MAX_VALUE.toLong()) return NonConvergingHierarchicalDistance()
        else HierarchicalDistance(distance + int)
    }
}

internal class NonConvergingHierarchicalDistance : HierarchicalDistance(Int.MAX_VALUE) {
    override fun incrementBy(int: Int): HierarchicalDistance {
        return this
    }
}

internal fun hierarchicalDistance(type: Class<*>, parent: Class<*>): HierarchicalDistance {
    return if (type == parent) HierarchicalDistance(0)
    /* Fast path */ else if (!parent.isAssignableFrom(type)) NonConvergingHierarchicalDistance()

    else {
        val interfaceHierarchy = type.interfaces.map { c ->
            hierarchicalDistance(c, parent).increment()
        }

        fun interfaceCount(cls: Class<*>) : Int {
            return cls.interfaces.size + cls.interfaces.map(::interfaceCount).sum()
        }

        val maxInterfaceHierarchy = interfaceCount(type)

        val superClass = type.superclass?.let { hierarchicalDistance(it, parent).incrementBy(maxInterfaceHierarchy) }

        (interfaceHierarchy + superClass)
            .filterNotNull()
            .minByOrNull { it.distance }
            ?: NonConvergingHierarchicalDistance()
    }
}

internal fun handleException(
//    serializers: List<ExceptionContextSerializer<*>>,
//    stackTracePrinter: StackTracePrinter,
    exception: StructuredException
) : String {
    val serializers = buildList {
        AnyContextSerializer().also(::add)
        IterableContextSerializer().also(::add)
        MapContextSerializer().also(::add)
        StringContextSerializer().also(::add)
        PathContextSerializer().also(::add)
    }
    val stackTracePrinter = BasicExceptionPrinter()

    fun serializeInternal(value: Any): String {
        // Super class distance is calculated as 1 + the max height of interfaces implemented by the current class

        val applicable = serializers.filter {
            it.type.isInstance(value)
        }.takeIf { it.isNotEmpty() }?.minBy {
            hierarchicalDistance(value::class.java, it.type).distance
        } ?: throw Exception("Cannot find serializer to serialize type: '$value' when handling a Job Exception. (has serializer 'any' not been registered?)")

        return (applicable as ExceptionContextSerializer<Any>).serialize(value,
            object : ExceptionContextSerializer.Helper {
                override fun serialize(value: Any): String {
                    return serializeInternal(value)
                }
            })
    }

    fun causes(exception: Throwable): List<Throwable> {
        return listOf(exception) + (exception.cause?.let(::causes) ?: listOf())
    }

    val causes = causes(exception)

    val completeContext = causes.filterIsInstance<StructuredException>().flatMap { it.context.entries }

    val output = StringBuilder()

    output.appendLine("Exception chain: ${causes.joinToString(separator = " <- ") { (it as? StructuredException)?.type?.toString() ?: it::class.java.simpleName }}")
    output.appendLine("A fatal exception has occurred:")
    output.appendLine(" --> " + (causes
        .reversed()
        .filterIsInstance<StructuredException>()
        .firstNotNullOfOrNull { it.message } ?: "No message provided"))

    if (completeContext.isNotEmpty()) {
        output.appendLine("Context:")
        completeContext.forEach { (k, v) ->
            output.appendLine(" > \"$k\" -> ${serializeInternal(v)}")
        }
    } else output.appendLine("Context: (none provided)")

    causes.filterIsInstance<StructuredException>().lastOrNull()?.let {
        output.append("Solutions:")
        if (it.solutions.isEmpty()) {
            output.appendLine(" (none detected)")
        } else {
            output.appendLine()
            it.solutions.forEach {
                output.appendLine(" - $it")
            }
        }
    }

    output.appendLine("Stacktrace (top-level cause):")

    val printWriter = PrintWriter(object : OutputStream() {
        override fun write(b: Int) {
            output.append(b.toChar())
        }
    })
    stackTracePrinter.printStacktrace(exception, printWriter)
    printWriter.flush()

    return output.toString()
}