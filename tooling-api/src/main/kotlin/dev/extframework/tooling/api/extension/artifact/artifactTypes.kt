package dev.extframework.tooling.api.extension.artifact

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.artifact.resolver.ArtifactRequest
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.durganmcbroom.resources.Resource
import dev.extframework.tooling.api.extension.ExtensionRuntimeModel

public data class ExtensionDescriptor(
    val group: String,
    val artifact: String,
    val version: String,
) : ArtifactMetadata.Descriptor {
    override val name: String = "$group:$artifact:$version"

    public companion object {
        public fun parseDescriptor(name: String) : ExtensionDescriptor {
            val (group, ext, version) = name.split(":").takeIf { it.size == 3 } ?: throw IllegalArgumentException("Invalid extension descriptor: $name")

            return ExtensionDescriptor(group, ext, version)
        }
    }

    override fun toString(): String = name
}

public data class ExtensionArtifactRequest(
    override val descriptor: ExtensionDescriptor
) : ArtifactRequest<ExtensionDescriptor>

public typealias ExtensionRepositorySettings = SimpleMavenRepositorySettings
public typealias ExtensionParentInfo = ArtifactMetadata.ParentInfo<ExtensionArtifactRequest, ExtensionRepositorySettings>

public class ExtensionArtifactMetadata(
    desc: ExtensionDescriptor,
    parents: List<ExtensionParentInfo>,
    public val erm: Resource,
//    public val erm: ExtensionRuntimeModel,
    public val repository: ExtensionRepositorySettings
) : ArtifactMetadata<ExtensionDescriptor, ExtensionParentInfo>(desc, parents)