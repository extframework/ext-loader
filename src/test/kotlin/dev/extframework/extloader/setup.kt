package dev.extframework.extloader

import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.job
import dev.extframework.boot.archive.ArchiveGraph
import dev.extframework.boot.archive.ArchiveTreeAuditContext
import dev.extframework.boot.archive.ArchiveTreeAuditor
import dev.extframework.boot.archive.DefaultArchiveGraph
import dev.extframework.boot.dependency.DependencyTypeContainer
import dev.extframework.boot.maven.MavenConstraintNegotiator
import dev.extframework.boot.maven.MavenDependencyResolver
import dev.extframework.boot.maven.MavenResolverProvider
import dev.extframework.boot.monad.removeIf
import dev.extframework.common.util.readInputStream
import dev.extframework.extloader.extension.DefaultExtensionResolver
import dev.extframework.`object`.ObjectContainerImpl
import dev.extframework.tooling.api.ExtensionLoader
import dev.extframework.tooling.api.environment.EnvironmentRegistry
import dev.extframework.tooling.api.environment.ExtensionEnvironment
import java.nio.file.Path
import kotlin.io.path.Path

fun newLoader(): Pair<ExtensionLoader, ExtensionEnvironment> {
    val path = Path("tests/cache")
    val (graph, types) = setupBoot(path)
    val environment = RootExtensionEnvironment(
        "root",
        path,
        types
    )

    val registry : EnvironmentRegistry = ObjectContainerImpl()

    val loader = DefaultExtensionLoader(
        DefaultExtensionResolver(
            ClassLoader.getSystemClassLoader(),
            registry,
            "root"
        ),
        graph,
        environment,
        registry
    )

    return Pair(loader, environment)
}

private class This

fun setupBoot(path: Path): Pair<ArchiveGraph, DependencyTypeContainer> {
    val dependencies = This::class.java.getResource("/dependencies.txt")?.openStream()?.use {
        val fileStr = String(it.readInputStream())
        fileStr.split("\n").toSet()
    }?.filterNot { it.isBlank() }?.mapTo(HashSet()) { SimpleMavenDescriptor.parseDescription(it)!! }
        ?: throw IllegalStateException("Cant load dependencies?")

    val archiveGraph = DefaultArchiveGraph(
        path,
    )

    val negotiator = MavenConstraintNegotiator()

    val alreadyLoaded = dependencies.map {
        negotiator.classify(it)
    }

    archiveGraph.auditors = archiveGraph.auditors.chain(object : ArchiveTreeAuditor {
        override fun audit(event: ArchiveTreeAuditContext): Job<ArchiveTreeAuditContext> = job {
            event.copy(tree = event.tree.removeIf {
                alreadyLoaded.contains(
                    negotiator.classify(
                        it.value.descriptor as? SimpleMavenDescriptor ?: return@removeIf false
                    )
                )
            }!!)
        }
    })

    val maven = MavenDependencyResolver(
        parentClassLoader = This::class.java.classLoader,
    )

    archiveGraph.registerResolver(maven)

    return archiveGraph to DependencyTypeContainer(archiveGraph).apply {
        register("simple-maven", MavenResolverProvider(resolver = maven))
    }
}