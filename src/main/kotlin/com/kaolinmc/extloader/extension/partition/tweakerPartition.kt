package com.kaolinmc.extloader.extension.partition

import com.kaolinmc.archives.ArchiveHandle
import com.kaolinmc.archives.ArchiveReference
import com.kaolinmc.boot.archive.ArchiveException
import com.kaolinmc.boot.archive.ArchiveNodeResolver
import com.kaolinmc.boot.archive.IArchive
import com.kaolinmc.boot.archive.TaggedIArchive
import com.kaolinmc.boot.monad.Either
import com.kaolinmc.boot.monad.Tagged
import com.kaolinmc.boot.monad.Tree
import com.kaolinmc.boot.util.mapAsync
import com.kaolinmc.common.util.runCatching
import com.kaolinmc.tooling.api.extension.PartitionRuntimeModel
import com.kaolinmc.tooling.api.extension.partition.*
import com.kaolinmc.tooling.api.extension.partition.artifact.PartitionArtifactMetadata
import com.kaolinmc.tooling.api.tweaker.EnvironmentTweaker
import kotlinx.coroutines.awaitAll
import java.nio.file.Path
import kotlin.io.path.toPath

public class TweakerPartitionLoader : ExtensionPartitionLoader<TweakerPartitionMetadata> {
    override val id: String = TYPE

    public companion object {
        public const val TYPE: String = "tweaker"
    }

    override fun parseMetadata(
        partition: PartitionRuntimeModel,
        reference: ArchiveReference?,
        helper: PartitionMetadataHelper,
    ): TweakerPartitionMetadata {
        if (reference == null) throw PartitionLoadException(
            partition.name,
            "The tweaker partition must have a jar."
        )

        val tweakerCls = partition.options["tweaker-class"]
            ?: throw IllegalArgumentException("Tweaker partition from extension: '${partition.name}' must contain a tweaker class defined as option: 'tweaker-class'.")

        return TweakerPartitionMetadata(tweakerCls)
    }

    override fun load(
        metadata: TweakerPartitionMetadata,
        reference: ArchiveReference?,
        accessTree: PartitionAccessTree,
        helper: PartitionLoaderHelper
    ): ExtensionPartitionContainer<*, TweakerPartitionMetadata> {
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
        }
            ?: throw IllegalArgumentException("Could not load tweaker partition: '${metadata.name}' because the class: '${metadata.tweakerClass}' couldnt be found.")

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

        return ExtensionPartitionContainer(helper.descriptor, metadata, node)
    }

    override suspend fun cache(
        metadata: PartitionArtifactMetadata,
        parents: List<Tree<Either<PartitionArtifactMetadata, TaggedIArchive>>>,
        helper: PartitionCacheHelper
    ): Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>> {
        val parents = helper.erm.parents
            .mapAsync {
                try {
                    helper.cache("tweaker", it)
                } catch (_: ArchiveException.ArchiveNotFound) {
                    // Nothing
                    null
                } catch (e: Throwable) {
                    throw e
                }
            }
            .awaitAll()
            .filterNotNull()

        return helper.newData(
            metadata.descriptor,
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