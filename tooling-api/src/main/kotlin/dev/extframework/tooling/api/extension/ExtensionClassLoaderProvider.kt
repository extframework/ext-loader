package dev.extframework.tooling.api.extension

//@Deprecated("This should be removed.")
//public interface ExtensionClassLoaderProvider : ExtensionEnvironment.Attribute {
//    override val key: ExtensionEnvironment.Attribute.Key<ExtensionClassLoaderProvider>
//        get() = ExtensionClassLoaderProvider
//
//    public fun createFor(
//        archive: ArchiveReference,
//        erm: ExtensionRuntimeModel,
////        partitions: List<ExtensionPartitionContainer<*, *>>,
//        parent: ClassLoader,
//    ): ExtensionClassLoader {
//        return ExtensionClassLoader(
//            erm.name, parent
//        )
//    }
//
//    public companion object : ExtensionEnvironment.Attribute.Key<ExtensionClassLoaderProvider>
//}