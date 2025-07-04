package dev.extframework.extloader

import dev.extframework.tooling.api.extension.artifact.ExtensionDescriptor
import dev.extframework.tooling.api.extension.artifact.ExtensionRepositorySettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test

class TestBlackbox {
    @Test
    fun `Test cache blackbox`(): Unit = runBlocking {
        val (loader) = newLoader()

        val descriptor = ExtensionDescriptor("dev.extframework.test", "blackbox", "1.0")
        val result = loader.cache(
            mapOf(descriptor to ExtensionRepositorySettings.local())
        )

        check(result.size == 1)
        check(result[0].item.descriptor == descriptor)
    }

    @Test
    fun `Test load blackbox`(): Unit = runBlocking {
        val (loader) = newLoader()

        val descriptor = ExtensionDescriptor("dev.extframework.test", "blackbox", "1.0")
        loader.cache(
            mapOf(descriptor to ExtensionRepositorySettings.local())
        )

        val nodes = loader.load(listOf(descriptor))

        check(nodes.size == 1)
    }

    @Test
    fun `Test run tweaker blackbox`(): Unit = runBlocking {
        val (loader, env) = newLoader()

        val descriptor = ExtensionDescriptor("dev.extframework.test", "blackbox", "1.0")
        loader.cache(
            mapOf(descriptor to ExtensionRepositorySettings.local())
        )

        val nodes = loader.load(listOf(descriptor))

        val workEnv = env.compose("worker")

        loader.tweak(nodes)

        check(System.getProperty("tweaker") == "true")
    }

    class MyCustomType(val str: String)

    suspend fun callThis() {
        val type = MyCustomType("Test")

        delay(100)

        println(type.str)
    }

    suspend fun secondCall() {
        delay(100)
    }

    @Test
    fun `Late night test`() {
        runBlocking {
            callThis()

            System.gc()

            println("Here")
        }
    }

    @Test
    fun `Test cleanup`() {
        val (loader) = newLoader()

        runBlocking {
            val descriptor = ExtensionDescriptor("dev.extframework.test", "blackbox", "1.0")
            loader.cache(
                mapOf(descriptor to ExtensionRepositorySettings.local())
            )

            val nodes = loader.load(listOf(descriptor))

            loader.tweak(nodes)

            loader.unload(descriptor)

            System.gc()

            println(loader)

            System.gc()

            delay(100)

            println("EHRE")
        }

        System.gc()

        check(System.getProperty("clean") == "true")

        println(loader)

        runBlocking {

        }
    }
}