package dev.extframework.tooling.api.environment

import dev.extframework.boot.dependency.DependencyResolverProvider
import dev.extframework.`object`.ObjectContainer
import dev.extframework.`object`.ObjectContainerImpl
import dev.extframework.tooling.api.environment.MutableSetAttribute
import dev.extframework.tooling.api.environment.MutableSetAttribute.Key
import dev.extframework.tooling.api.exception.ExceptionContextSerializer
import dev.extframework.tooling.api.extension.partition.ExtensionPartitionLoader
import java.nio.file.Path

public val dependencyTypesAttrKey: ObjectContainerAttribute.Key<DependencyResolverProvider<*, *, *>> =
    ObjectContainerAttribute.Key("dependency-types")
public val partitionLoadersAttrKey: ObjectContainerAttribute.Key<ExtensionPartitionLoader<*>> =
    ObjectContainerAttribute.Key("partition-loader")
public val exceptionCxtSerializersAttrKey: MutableSetAttribute.Key<ExceptionContextSerializer<*>> =
    MutableSetAttribute.Key("exception-context-serializer")
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

//public open class DependencyTypeContainerAttribute(
//    public open val container: DependencyTypeContainer
//) : ExtensionEnvironment.Attribute {
//    override val key: ExtensionEnvironment.Attribute.Key<*> = DependencyTypeContainerAttribute
//
//    public companion object : ExtensionEnvironment.Attribute.Key<DependencyTypeContainerAttribute>
//
//    private class View(
//        override var reference: DependencyTypeContainerAttribute
//    ) : DependencyTypeContainerAttribute(reference.container),
//        ExtensionEnvironment.Attribute.View<DependencyTypeContainerAttribute> {
//        override var isValid: Boolean = true
//
//        override val container: DependencyTypeContainer = DependencyTypeContainerView()
//
//        inner class DependencyTypeContainerView() :
//            DependencyTypeContainer(reference.container.archiveGraph),
//            MutableObjectContainer<DependencyResolverProvider<*, *, *>> by
//            MutableObjectContainerView({
//                reference.container
//            }) {
//
//            }
//    }
//    }


/**
 * T must not be a mutable type.
 */
public open class ObjectContainerAttribute<T : ObjectContainer.IDed>(
    override val key: Key<T>,
    public open val container: ObjectContainer<T> = ObjectContainerImpl()
) : ExtensionEnvironment.Attribute {
    public constructor(name: String, delegate: ObjectContainer<T> = ObjectContainerImpl()) : this(
        Key<T>(name),
        delegate
    )

    public data class Key<T : ObjectContainer.IDed>(
        val name: String
    ) : ExtensionEnvironment.Attribute.Key<ObjectContainerAttribute<T>>

    override fun compose(into: ExtensionEnvironment): ExtensionEnvironment.Attribute.View<*>? {
        return View(this)
    }

    private class View<T : ObjectContainer.IDed>(
        override var reference: ObjectContainerAttribute<T>
    ) : ObjectContainerAttribute<T>(reference.key, reference.container),
        ExtensionEnvironment.Attribute.View<ObjectContainerAttribute<T>> {
        override var isValid: Boolean = true

        override val container: ObjectContainer<T> = ObjectContainerView {
            reference.container
        }
    }
}

public open class MutableListAttribute<T>(
    override val key: Key<T>,
    delegate: MutableList<T> = ArrayList(),
) : ExtensionEnvironment.Attribute, MutableList<T> by delegate {
    public constructor(name: String) : this(Key(name))

    public data class Key<T>(
        val name: String
    ) : ExtensionEnvironment.Attribute.Key<MutableListAttribute<T>>

    override fun compose(into: ExtensionEnvironment): ExtensionEnvironment.Attribute.View<*>? {
        return View(this)
    }

    private class View<T>(
        override var reference: MutableListAttribute<T>
    ) : MutableListAttribute<T>(
        reference.key,
        ListView { reference }
    ), ExtensionEnvironment.Attribute.View<MutableListAttribute<T>> {
        override var isValid: Boolean = true
    }
}

public open class MutableSetAttribute<T>(
    override val key: Key<T>,
    delegate: MutableSet<T> = HashSet(),
) : ExtensionEnvironment.Attribute, MutableSet<T> by delegate {
    public constructor(name: String) : this(Key(name))

    public data class Key<T>(
        val name: String
    ) : ExtensionEnvironment.Attribute.Key<MutableSetAttribute<T>>

    override fun compose(into: ExtensionEnvironment): ExtensionEnvironment.Attribute.View<*>? {
        return View(this)
    }

    private class View<T>(
        override var reference: MutableSetAttribute<T>,
    ) : MutableSetAttribute<T>(
        reference.key,
        SetView { reference }
    ),
        ExtensionEnvironment.Attribute.View<MutableSetAttribute<T>> {
        override var isValid: Boolean = true
    }
}

/**
 * VERY IMPORTANT: The value stored in this attribute SHOULD NOT be mutable as it break environment composition
 */
public open class ValueAttribute<T>(
    override val key: Key<T>,
    public val value: T
) : ExtensionEnvironment.Attribute {
    public data class Key<T>(
        val name: String
    ) : ExtensionEnvironment.Attribute.Key<ValueAttribute<T>>
}
