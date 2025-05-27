package dev.extframework.tooling.api.environment

import dev.extframework.boot.dependency.DependencyTypeContainer
import dev.extframework.`object`.MutableObjectContainer
import dev.extframework.`object`.ObjectContainerImpl
import dev.extframework.tooling.api.exception.ExceptionContextSerializer
import dev.extframework.tooling.api.extension.partition.ExtensionPartitionLoader
import java.nio.file.Path

public val dependencyTypesAttrKey: DependencyTypeContainerAttribute.Companion =
    DependencyTypeContainerAttribute
public val partitionLoadersAttrKey : MutableObjectContainerAttribute.Key<ExtensionPartitionLoader<*>> =
    MutableObjectContainerAttribute.Key("partition-loader")
public val exceptionCxtSerializersAttrKey : MutableObjectSetAttribute.Key<ExceptionContextSerializer<*>>
    = MutableObjectSetAttribute.Key("exception-context-serializer")
public val wrkDirAttrKey: ValueAttribute.Key<Path> = ValueAttribute.Key("working-directory")
public val parentCLAttrKey: ValueAttribute.Key<ClassLoader> = ValueAttribute.Key("parent-classloader")

//public val ExtensionEnvironment.archiveGraph: ArchiveGraph
//    get() = get(ArchiveGraphAttribute).getOrNull()!!.graph

//public data class ArchiveGraphAttribute(
//    val graph: ArchiveGraph
//) : EnvironmentAttribute {
//    override val key: EnvironmentAttributeKey<*> = ArchiveGraphAttribute
//
//    public companion object : EnvironmentAttributeKey<ArchiveGraphAttribute>
//}

public class DependencyTypeContainerAttribute(
    public val container: DependencyTypeContainer
) : ExtensionEnvironment.Attribute {
    override val key: ExtensionEnvironment.Attribute.Key<*> = DependencyTypeContainerAttribute

    public companion object : ExtensionEnvironment.Attribute.Key<DependencyTypeContainerAttribute>
}

public open class MutableObjectContainerAttribute<T>(
    override val key: Key<T>,
    public open val container: MutableObjectContainer<T> = ObjectContainerImpl()
) : ExtensionEnvironment.Attribute {
    public constructor(name: String, delegate: MutableObjectContainer<T> = ObjectContainerImpl()) : this(
        Key<T>(name),
        delegate
    )

    public data class Key<T>(
        val name: String
    ) : ExtensionEnvironment.Attribute.Key<MutableObjectContainerAttribute<T>>
}

public open class MutableObjectSetAttribute<T>(
    override val key: Key<T>,
) : ExtensionEnvironment.Attribute, ArrayList<T>() {
    public data class Key<T>(
        val name: String
    ) : ExtensionEnvironment.Attribute.Key<MutableObjectSetAttribute<T>>
}

public open class ValueAttribute<T>(
    public val value: T,
    override val key: Key<T>
) : ExtensionEnvironment.Attribute {
    public data class Key<T>(
        val name: String
    ) : ExtensionEnvironment.Attribute.Key<ValueAttribute<T>>
}
