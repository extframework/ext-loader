package net.yakclient.components.extloader.test.exception

import net.yakclient.components.extloader.api.environment.MutableObjectSetAttribute
import net.yakclient.components.extloader.api.environment.exceptionContextSerializers
import net.yakclient.components.extloader.api.exception.ExceptionContextSerializer
import net.yakclient.components.extloader.api.exception.ExceptionType
import net.yakclient.components.extloader.api.exception.StructuredException
import net.yakclient.components.extloader.environment.registerBasicSerializers
import net.yakclient.components.extloader.exception.BasicExceptionPrinter
import net.yakclient.components.extloader.exception.handleException
import net.yakclient.components.extloader.exception.hierarchicalDistance
import kotlin.test.Test

class TestExceptionPrinting {
    @Test
    fun `Test list hierarchical distance`() {
        val distance = hierarchicalDistance(ArrayList::class.java, List::class.java)

        check(distance.distance == 1)
    }

    @Test
    fun `Test arrayList to iterable hierarchical distance`() {
        val distance = hierarchicalDistance(ArrayList::class.java, Iterable::class.java)
        check(distance.distance == 3)
    }

    private enum class BasicExceptionCauses : ExceptionType {
        Basic
    }


    @Test
    fun `Print exception without context`() {
        val exception = StructuredException(BasicExceptionCauses.Basic)

        val container = MutableObjectSetAttribute<ExceptionContextSerializer<*>>(exceptionContextSerializers)
        container.registerBasicSerializers()

        handleException(
            container.toList(),
            BasicExceptionPrinter(),
            exception,
        )
    }

    @Test
    fun `Print with with only any context values`() {
        val exception = StructuredException(BasicExceptionCauses.Basic) {
            "Yo how are you doing" asContext "A random greeting"
            100100 asContext "A random number"
        }

        val container = MutableObjectSetAttribute<ExceptionContextSerializer<*>>(exceptionContextSerializers)
        container.registerBasicSerializers()

        handleException(
            container.toList(),
            BasicExceptionPrinter(),
            exception
        )
    }

    @Test
    fun `Print with iterable context values`() {
        val exception = StructuredException(BasicExceptionCauses.Basic, NullPointerException(), "test message") {
            listOf("One item", "A second item", " A third item") asContext "A random list of strings"
            setOf(5, 8, 9) asContext "A random set of numbers"
            mapOf("Sheep in barn" to 5, "Cows in shed" to 101, "Secret number" to 111) asContext "A map"
            listOf(listOf("a", "b"), listOf("one", "two", "three", "five?"), listOf()) asContext "list of lists"
        }

        val container = MutableObjectSetAttribute<ExceptionContextSerializer<*>>(exceptionContextSerializers)
        container.registerBasicSerializers()

        handleException(
            container.toList(),
            BasicExceptionPrinter(),
            exception
        )
    }
}