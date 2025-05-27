package dev.extframework.extloader

import BootLoggerFactory
import com.durganmcbroom.jobs.launch
import dev.extframework.tooling.api.extension.artifact.ExtensionDescriptor
import dev.extframework.tooling.api.extension.artifact.ExtensionRepositorySettings
import dev.extframework.tooling.api.uber.UberDescriptor
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class TestBlackbox {
    @Test
    fun `Test cache blackbox`(): Unit = launch(BootLoggerFactory()) {
        runBlocking {
            val (loader, env) = newLoader()

            val descriptor = ExtensionDescriptor("dev.extframework.test", "blackbox", "1.0")
            val result = loader.cache(
                mapOf(descriptor to ExtensionRepositorySettings.local())
            )().merge()

            check(result.size == 1)
            check(result[0].item.descriptor == descriptor)
        }
    }

    @Test
    fun `Test load blackbox`(): Unit = launch(BootLoggerFactory()) {
        runBlocking {
            val (loader, env) = newLoader()

            val descriptor = ExtensionDescriptor("dev.extframework.test", "blackbox", "1.0")
            loader.cache(
                mapOf(descriptor to ExtensionRepositorySettings.local())
            )().merge()

            loader.load(listOf(descriptor))().merge()

            check(loader.loaded.size == 1)
        }
    }

    @Test
    fun `Test run tweaker blackbox`(): Unit = launch(BootLoggerFactory()) {
        runBlocking {
            val (loader, env) = newLoader()

            val descriptor = ExtensionDescriptor("dev.extframework.test", "blackbox", "1.0")
            loader.cache(
                mapOf(descriptor to ExtensionRepositorySettings.local())
            )().merge()

            loader.load(listOf(descriptor))().merge()

            val workEnv = env.compose("worker")

            loader.tweak(workEnv)().merge()

            check(System.getProperty("tweaker") == "true")
        }
    }

    @Test
    fun `Test cleanup`(): Unit = launch(BootLoggerFactory()) {
        val (loader, env) = newLoader()

        runCatching {
            runBlocking {
                val descriptor = ExtensionDescriptor("dev.extframework.test", "blackbox", "1.0")
                loader.cache(
                    mapOf(descriptor to ExtensionRepositorySettings.local())
                )().merge()

                loader.load(listOf(descriptor))().merge()

                val workEnv = env.compose("worker")

                loader.tweak(workEnv)().merge()

                loader.unload(descriptor)().merge()

                System.gc()

                check(System.getProperty("clean") == "true")
//                check(loader.environment[ExtensionInitializer].getOrNull() == null)
            }
        }.handleStructuredException()
    }
}