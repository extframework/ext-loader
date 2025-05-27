package dev.extframework.extloader.extension.artifact

import com.durganmcbroom.artifact.resolver.RepositoryFactory
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import dev.extframework.boot.dependency.DependencyTypeContainer
import dev.extframework.tooling.api.extension.artifact.ExtensionRepositorySettings

public class ExtensionRepositoryFactory(
) : RepositoryFactory<ExtensionRepositorySettings, ExtensionArtifactRepository> {
    override fun createNew(settings: SimpleMavenRepositorySettings): ExtensionArtifactRepository {
        return ExtensionArtifactRepository(
            settings,
            this
        )
    }
}