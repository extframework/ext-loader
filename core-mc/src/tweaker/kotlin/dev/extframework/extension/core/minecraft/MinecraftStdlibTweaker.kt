package dev.extframework.extension.core.minecraft

import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.job
import com.durganmcbroom.jobs.logging.info
import dev.extframework.extension.core.annotation.AnnotationProcessor
import dev.extframework.extension.core.minecraft.environment.ApplicationMappingTarget
import dev.extframework.extension.core.minecraft.environment.mappingProvidersAttrKey
import dev.extframework.extension.core.minecraft.environment.remappersAttrKey
import dev.extframework.extension.core.minecraft.internal.RootRemapper
import dev.extframework.extension.core.minecraft.internal.SourceInjectionRemapper
import dev.extframework.extension.core.minecraft.partition.MinecraftPartitionLoader
import dev.extframework.internal.api.environment.*
import dev.extframework.internal.api.tweaker.EnvironmentTweaker

public class MinecraftStdlibTweaker : EnvironmentTweaker {
    override fun tweak(environment: ExtensionEnvironment): Job<Unit> = job {
        environment += MutableObjectSetAttribute(mappingProvidersAttrKey)

        val partitionContainer = environment[partitionLoadersAttrKey].extract().container
        partitionContainer.register("target", MinecraftPartitionLoader(environment))

        val remappers = MutableObjectSetAttribute(remappersAttrKey)
        environment += remappers
        remappers.add(RootRemapper())
        remappers.add(SourceInjectionRemapper(environment[AnnotationProcessor].extract()))

        environment[ApplicationMappingTarget].extract()
    }
}