package com.kaolinmc.extloader.extension.artifact

import com.durganmcbroom.artifact.resolver.ArtifactRepository
import com.durganmcbroom.artifact.resolver.MetadataRequestException
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.durganmcbroom.artifact.resolver.simple.maven.layout.ResourceRetrievalException
import com.durganmcbroom.resources.ResourceAlgorithm
import com.durganmcbroom.resources.ResourceNotFoundException
import com.durganmcbroom.resources.toByteArray
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.kaolinmc.extloader.exception.ExtLoaderExceptions
import com.kaolinmc.tooling.api.TOOLING_API_VERSION
import com.kaolinmc.tooling.api.exception.StructuredException
import com.kaolinmc.tooling.api.extension.ExtensionRuntimeModel
import com.kaolinmc.tooling.api.extension.artifact.*
import com.kaolinmc.tooling.api.extension.descriptor

public open class ExtensionArtifactRepository(
    final override val settings: SimpleMavenRepositorySettings,
    override val factory: ExtensionRepositoryFactory,
) : ArtifactRepository<SimpleMavenRepositorySettings, ExtensionArtifactRequest, ExtensionArtifactMetadata> {
    override val name: String = "extensions@${settings.layout.name}"
    private val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
    private val layout by settings::layout

    override suspend fun get(
        request: ExtensionArtifactRequest
    ): ExtensionArtifactMetadata {
        val (group, artifact, version) = request.descriptor

        val (ermOr, ermLocation) = try {
            val resource = layout.resourceOf(group, artifact, version, "erm", "json")

            resource to resource.location
        } catch (e: ResourceNotFoundException) {
            throw MetadataRequestException.MetadataNotFound(request.descriptor, "erm.json", e)
        } catch (e: Exception) {
            throw MetadataRequestException("Failed to request resource for erm: '${request.descriptor}'", e)
        }

        val ermBytes = ermOr.open().toByteArray()
        verifyVersion(request.descriptor.name, mapper.readTree(ermBytes))

        val erm = try {
            mapper.readValue<ExtensionRuntimeModel>(ermBytes)
        } catch (e: Exception) {
            throw StructuredException(
                ExtLoaderExceptions.InvalidErm,
                description = "Invalid Extension runtime model built for extension: '${request.descriptor}'",
                cause = e
            ) {
                TOOLING_API_VERSION asContext "Current API version:"
            }
        }

        validateErm(request.descriptor, erm)

        val children = erm.parents

        return ExtensionArtifactMetadata(
            request.descriptor,
            children.map { req1 ->
                ExtensionParentInfo(
                    ExtensionArtifactRequest(req1.toDescriptor()),
                    erm.repositories.map { settings ->
                        parseSettings(settings)
                            ?: throw ResourceRetrievalException.IllegalState("Illegal repository declaration: '$settings' in extension runtime model: '${request.descriptor}' at '${ermLocation}'. Cannot parse.")
                    },
                )
            },
            ermOr,
            settings
        )
    }

    private fun verifyVersion(
        extension: String,
        node: JsonNode,
    ) {
        val apiVersion = node.get("apiVersion")?.asInt() ?: 0

        if (!(2..TOOLING_API_VERSION).contains(apiVersion)) {
            throw MetadataRequestException("Extension: '$extension' is not compatible with this Tooling API version")
        }
    }

    private fun validateErm(
        descriptor: ExtensionDescriptor,
        erm: ExtensionRuntimeModel,
    ) {
        if (erm.descriptor != descriptor) {
            throw StructuredException(
                ExtLoaderExceptions.InvalidErm,
                description = "Descriptor mismatch. The group:name:version in the erm must match the path at which this artifact is located."
            ) {
                erm.descriptor asContext "ERM descriptor"
            }
        }
        if (erm.apiVersion > TOOLING_API_VERSION) {
            throw StructuredException(
                ExtLoaderExceptions.InvalidErm,
                description = "Unsupported API version."
            ) {
                erm.apiVersion asContext "Extension API version"
                TOOLING_API_VERSION asContext "Current API version"
            }
        }
    }

    internal companion object {
        fun parseSettings(settings: Map<String, String>): ExtensionRepositorySettings? {
            val location = settings["location"] ?: return null
            val preferredHash = settings["preferredHash"] ?: "SHA1"
            val type = settings["type"] ?: "default"

            val hashType = ResourceAlgorithm.valueOf(preferredHash)

            return when (type) {
                "default" -> SimpleMavenRepositorySettings.default(
                    location,
                    true,
                    false,
                    hashType
                )

                "local" -> SimpleMavenRepositorySettings.local(location, hashType)
                else -> return null
            }
        }
    }
}