package com.kaolinmc.extloader.extension.partition

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.artifact.resolver.ArtifactRequest
import com.durganmcbroom.artifact.resolver.RepositorySettings
import com.kaolinmc.boot.archive.ArchiveException
import com.kaolinmc.boot.archive.ArchiveNodeResolver
import com.kaolinmc.boot.archive.CacheHelper
import com.kaolinmc.boot.archive.IArchive
import com.kaolinmc.boot.dependency.DependencyResolver
import com.kaolinmc.boot.dependency.DependencyResolverProvider
import com.kaolinmc.boot.dependency.DependencyTypeContainer
import com.kaolinmc.boot.monad.Tagged
import com.kaolinmc.boot.monad.Tree
import com.kaolinmc.boot.util.mapAsync
import com.kaolinmc.tooling.api.extension.PartitionRuntimeModel
import com.kaolinmc.tooling.api.extension.partition.PartitionLoadException
import kotlinx.coroutines.Deferred

internal suspend fun cachePartitionDependencies(
    partition: PartitionRuntimeModel,
    extName: String,
    dependencyProviders: DependencyTypeContainer,
    helper: CacheHelper<*>
): List<Deferred<Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>>> =
    partition.dependencies.mapAsync { dependency ->
        if (partition.repositories.isEmpty()) {
            throw PartitionLoadException(
                partition.name, "Partition: '${partition.name}' has no defined repositories but has dependencies!"
            ) {
                extName asContext "Extension name"

                solution("Define at least 1 repository in this partition.")
            }
        }

        val requests = partition.repositories.mapNotNull { settings ->
            val provider: DependencyResolverProvider<*, *, *> =
                dependencyProviders.get(settings.type) ?: throw PartitionLoadException(
                    partition.name, "Failed to find dependency type: '${settings.type}'"
                ) {
                    partition.name asContext "Partition name"
                }

            val depReq: ArtifactRequest<*> = provider.parseRequest(dependency) ?: return@mapNotNull null

            val repoSettings = provider.parseSettings(settings.settings) ?: throw PartitionLoadException(
                partition.name, "Invalid repository settings."
            ) {
                extName asContext "Extension name"
                settings.settings asContext "Repository settings"
                provider.id asContext "Dependency resolution provider"
            }

            Triple(depReq, repoSettings, provider)
        }

        if (requests.isEmpty()) {
            throw PartitionLoadException(
                partition.name,
                "Failed to parse dependency request.",
            ) {
                extName asContext "Extension name"
                dependency asContext "Raw dependency request"
                partition.repositories asContext "Attempted repositories"
            }
        }

        val cacheResult = requests.map findRepo@{ (request, settings, provider) ->
            val resolver =
                provider.resolver as DependencyResolver<ArtifactMetadata.Descriptor, ArtifactRequest<ArtifactMetadata.Descriptor>, *, RepositorySettings, *>

            runCatching {
                helper.cache(
                    request as ArtifactRequest<ArtifactMetadata.Descriptor>, settings, resolver
                )
            }
        }

        if (cacheResult.all { it.exceptionOrNull() is ArchiveException.ArchiveNotFound }) {
            val cause =
                cacheResult.mapNotNull { it.exceptionOrNull() }.filterIsInstance<ArchiveException.ArchiveNotFound>()
                    .first()

            throw PartitionLoadException(
                partition.name, "a dependency couldn't be located.", cause = cause
            ) {
                extName asContext "Extension name"
                cause.archive asContext "Raw dependency request" // We want the raw dependency request because there was an issue with every single dependency provider (and we correctly assume that all may have different dependency descriptor types)
                cause.lookedIn asContext "Attempted repositories"

                solution("Make sure all repositories are defined correctly and contain the requested artifact.")
            }
        }

        cacheResult.find { cacheResult ->
            cacheResult.isSuccess
        }?.getOrNull() ?: throw PartitionLoadException(
            partition.name,
            "An unrecoverable error occurred when caching dependencies",
            cacheResult.mapNotNull { it.exceptionOrNull() }.first { it !is ArchiveException.ArchiveNotFound }) {
            dependency asContext "Raw dependency request" // We want the raw dependency request because there was an issue with every single dependency provider (and we correctly assume that all may have different dependency descriptor types)
            requests.map { it.second } asContext "Attempted repositories"
            extName asContext "Extension name"
        }
    }
