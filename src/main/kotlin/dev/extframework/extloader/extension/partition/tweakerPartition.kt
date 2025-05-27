package dev.extframework.extloader.extension.partition

import com.durganmcbroom.artifact.resolver.Artifact
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.async.AsyncJob
import com.durganmcbroom.jobs.async.asyncJob
import com.durganmcbroom.jobs.async.mapAsync
import com.durganmcbroom.jobs.job
import dev.extframework.archives.ArchiveHandle
import dev.extframework.archives.ArchiveReference
import dev.extframework.boot.archive.ArchiveException
import dev.extframework.boot.archive.ArchiveNodeResolver
import dev.extframework.boot.archive.IArchive
import dev.extframework.boot.monad.Tagged
import dev.extframework.boot.monad.Tree
import dev.extframework.common.util.runCatching
import dev.extframework.tooling.api.extension.PartitionRuntimeModel
import dev.extframework.tooling.api.extension.descriptor
import dev.extframework.tooling.api.extension.partition.*
import dev.extframework.tooling.api.extension.partition.artifact.PartitionArtifactMetadata
import dev.extframework.tooling.api.extension.partition.artifact.partition
import dev.extframework.tooling.api.tweaker.EnvironmentTweaker
import kotlinx.coroutines.awaitAll
import java.nio.file.Path
import kotlin.io.path.toPath

public class TweakerPartitionLoader : ExtensionPartitionLoader<TweakerPartitionMetadata> {
    override val type: String = TYPE

    public companion object {
        public const val TYPE: String = "tweaker"
    }

    override fun parseMetadata(
        partition: PartitionRuntimeModel,
        reference: ArchiveReference?,
        helper: PartitionMetadataHelper,
    ): Job<TweakerPartitionMetadata> = job {
        if (reference == null) throw PartitionLoadException(
            partition.name,
            "The tweaker partition must have a jar."
        )

        val tweakerCls = partition.options["tweaker-class"]
            ?: throw IllegalArgumentException("Tweaker partition from extension: '${partition.name}' must contain a tweaker class defined as option: 'tweaker-class'.")

        TweakerPartitionMetadata(tweakerCls)
    }

    override fun load(
        metadata: TweakerPartitionMetadata,
        reference: ArchiveReference?,
        accessTree: PartitionAccessTree,
        helper: PartitionLoaderHelper
    ): Job<ExtensionPartitionContainer<*, TweakerPartitionMetadata>> = job {
        if (reference == null) throw PartitionLoadException(
            metadata.name,
            "The tweaker partition must have a jar."
        )

        val cl = PartitionClassLoader(
            helper.descriptor,
            accessTree,
            reference,
            helper.parentClassLoader
        )

        val handle = PartitionArchiveHandle(
            helper.descriptor.name,
            cl,
            reference,
            setOf()
        )

        val extensionClass = runCatching(ClassNotFoundException::class) {
            handle.classloader.loadClass(
                metadata.tweakerClass
            )
        } ?: throw IllegalArgumentException("Could not load tweaker partition: '${metadata.name}' because the class: '${metadata.tweakerClass}' couldnt be found.")

        val extensionConstructor =
            runCatching(NoSuchMethodException::class) { extensionClass.getConstructor() }
                ?: throw IllegalArgumentException("Could not find no-arg constructor in class: '${metadata.tweakerClass}' in partition: '${metadata.name}'.")

        val instance = extensionConstructor.newInstance() as? EnvironmentTweaker
            ?: throw IllegalArgumentException("Tweaker class: '${metadata.tweakerClass}' does not implement: '${EnvironmentTweaker::class.qualifiedName} in extension: '${metadata.name}'.")

        val node = TweakerPartitionNode(
            handle,
            accessTree,
            instance,
            reference.location.toPath(),
        )

        ExtensionPartitionContainer(helper.descriptor, metadata, node)
    }

    override fun cache(
        artifact: Artifact<PartitionArtifactMetadata>,
        helper: PartitionCacheHelper
    ): AsyncJob<Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>> = asyncJob {
        val parents = helper.erm.parents
            .mapAsync {
                val result = helper.cache("tweaker", helper.defaultEnvironment, it)()

                val exception = result.exceptionOrNull()

                if (exception != null && exception !is ArchiveException.ArchiveNotFound) {
                    throw exception
                }

                result.getOrNull()
            }
            .awaitAll()
            .filterNotNull()

        helper.newData(
            artifact.metadata.descriptor,
            parents
        )
    }
}

public data class TweakerPartitionMetadata(
    val tweakerClass: String
) : ExtensionPartitionMetadata {
    override val name: String = TweakerPartitionLoader.TYPE
}

public data class TweakerPartitionNode(
    override val archive: ArchiveHandle,
    override val access: PartitionAccessTree,
    val tweaker: EnvironmentTweaker,
    val jarPath: Path
) : ExtensionPartition