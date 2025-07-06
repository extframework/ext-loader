package com.kaolinmc.extloader

import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenArtifactRequest
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.kaolinmc.boot.archive.DefaultArchiveGraph
import com.kaolinmc.boot.maven.MavenDependencyResolver
import com.kaolinmc.tooling.api.ExtensionLoader
import kotlinx.coroutines.runBlocking
import kotlin.io.path.Path
import kotlin.test.Test

class TestView {
    @Test
    fun `Test Archive Graph load view`() {
        val (graph, types) = setupBoot(Path("tests/view"))
        val view = ArchiveGraphView { graph }

        val maven = types["simple-maven"]!!.resolver as MavenDependencyResolver

        runBlocking {
            val guavaRequest = SimpleMavenArtifactRequest("com.google.guava:guava:33.4.8-jre")
            graph.cache(
                guavaRequest,
                SimpleMavenRepositorySettings.mavenCentral(),
                maven
            )
            val guavaNode = graph.get(
                guavaRequest.descriptor, maven
            )

            check(view.nodes[guavaRequest.descriptor]?.value == guavaNode)

            val commonsRequest = SimpleMavenArtifactRequest("commons-io:commons-io:2.19.0")
            view.cache(
                commonsRequest,
                SimpleMavenRepositorySettings.mavenCentral(),
                maven
            )
            val commonsNode = view.get(
                commonsRequest.descriptor, maven
            )

            check(graph.nodes[commonsRequest.descriptor] == null)
        }
    }

    @Test
    fun `Test Archive Graph resolver and auditor view`() {
        val graph = DefaultArchiveGraph(Path("tests/view"))
        val view = ArchiveGraphView { graph }

        view.resolvers.register(MavenDependencyResolver(ClassLoader.getSystemClassLoader()))

        check(view.resolvers.size == 1)
        check(graph.resolvers.isEmpty())

        check(view.auditors.auditors.isNotEmpty())
        check(graph.auditors.auditors.isEmpty())
    }

    @Test
    fun `Test Extension loader view`() {
       val (loader, env) = newLoader()

        val childEnv = env.compose("child")

        childEnv[ExtensionLoader]
    }
}