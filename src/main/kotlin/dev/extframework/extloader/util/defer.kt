//package dev.extframework.extloader.util
//
//import com.durganmcbroom.artifact.resolver.ArtifactMetadata
//import dev.extframework.boot.archive.ArchiveNode
//import dev.extframework.boot.archive.ArchiveRelationship
//import dev.extframework.boot.archive.ArchiveTarget
//import java.io.InputStream
//import java.net.URL
//import java.util.*
//
//public fun DeferredValue<out ClassLoader>.defer(): ClassLoader = object : ClassLoader() {
//    override fun getResource(name: String): URL? = extract().getResource(name)
//
//    override fun getResourceAsStream(name: String): InputStream? = extract().getResourceAsStream(name)
//
//    override fun getResources(name: String): Enumeration<URL>? = extract().getResources(name)
//
//    override fun loadClass(name: String): Class<*> = extract().loadClass(name)
//
//    override fun toString(): String {
//        return getOrNull()?.toString() ?: "uninitialized"
//    }
//
//    override fun findResources(name: String?): Enumeration<URL> = extract().getResources(name)
//}
//
//public fun DeferredValue<ArchiveTarget>.defer(descriptor: ArtifactMetadata.Descriptor) : ArchiveTarget {
//    return  ArchiveTarget(
//        descriptor,
//        object: ArchiveRelationship {
//            override val name: String
//                get() = extract().relationship.name
//            override val node: ArchiveNode<*>
//                get() = extract().relationship.node
//
//        }
//    )
//}