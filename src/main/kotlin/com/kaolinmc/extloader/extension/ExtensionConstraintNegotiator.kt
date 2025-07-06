package com.kaolinmc.extloader.extension

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.kaolinmc.boot.archive.ArchiveTrace
import com.kaolinmc.boot.constraint.Constrained
import com.kaolinmc.boot.constraint.ConstraintNegotiator
import com.kaolinmc.boot.maven.MavenConstraintNegotiator

public class ExtensionConstraintNegotiator<T : ArtifactMetadata.Descriptor>(
    override val descriptorType: Class<T>,
    private val classify: (T) -> Any,
    private val convert: (T) -> SimpleMavenDescriptor
) : ConstraintNegotiator<T> {
    private val maven = MavenConstraintNegotiator()

    override fun classify(descriptor: T): Any {
       return descriptorType.name + classify.invoke(descriptor)
    }

    override fun negotiate(
        constraints: Set<Constrained<T>>,
        trace: ArchiveTrace
    ): T {
        val conversion = HashMap<SimpleMavenDescriptor, T>()

        return maven.negotiate(constraints.mapTo(HashSet()) {
            val mavenDesc = convert(it.descriptor)
            conversion[mavenDesc] = it.descriptor

            Constrained(
                mavenDesc,
                it.type
            )
        }, trace).let {
            conversion[it]!!
        }
    }
}