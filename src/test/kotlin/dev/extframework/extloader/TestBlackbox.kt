package dev.extframework.extloader

import dev.extframework.tooling.api.extension.artifact.ExtensionDescriptor
import dev.extframework.tooling.api.extension.artifact.ExtensionRepositorySettings
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class TestBlackbox {
    @Test
    fun `Test cache blackbox`(): Unit = runBlocking {
        val (loader, env) = newLoader()

        val descriptor = ExtensionDescriptor("dev.extframework.test", "blackbox", "1.0")
        val result = loader.cache(
            mapOf(descriptor to ExtensionRepositorySettings.local())
        )

        check(result.size == 1)
        check(result[0].item.descriptor == descriptor)
    }

    @Test
    fun `Test load blackbox`(): Unit = runBlocking {
        val (loader, env) = newLoader()

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

        loader.tweak(nodes,workEnv)

        check(System.getProperty("tweaker") == "true")
    }

    //TODO
    @Test
    fun `Test cleanup`(): Unit = runBlocking {

        val (loader, env) = newLoader()

        val descriptor = ExtensionDescriptor("dev.extframework.test", "blackbox", "1.0")
        loader.cache(
            mapOf(descriptor to ExtensionRepositorySettings.local())
        )

        val nodes = loader.load(listOf(descriptor))

        val workEnv = env.compose("worker")

        loader.tweak(nodes,workEnv)

//        loader.unload(descriptor)

        System.gc()

        check(System.getProperty("clean") == "true")
    }
}