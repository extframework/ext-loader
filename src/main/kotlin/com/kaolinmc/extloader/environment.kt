package com.kaolinmc.extloader

import com.kaolinmc.tooling.api.environment.*
import java.util.concurrent.ConcurrentHashMap

//public class RootExtensionEnvironment private constructor(override val id: String = "root") : ExtensionEnvironment {
//    private val attributes: MutableMap<ExtensionEnvironment.Attribute.Key<*>, ExtensionEnvironment.Attribute> =
//        ConcurrentHashMap()
//
//    public val workingDir: Path
//        get() = get(wrkDirAttrKey).value
//    public val dependencyTypes: DependencyTypeContainer
//        get() = get(dependencyTypesAttrKey).container
//
//    public constructor(
//        name: String,
//        workingDir: Path,
//        dependencyTypes: DependencyTypeContainer,
//    ) : this(name) {
//        attributes[wrkDirAttrKey] = (ValueAttribute(workingDir, wrkDirAttrKey))
//        attributes[dependencyTypesAttrKey] = (DependencyTypeContainerAttribute(dependencyTypes))
//        attributes[parentCLAttrKey] = ValueAttribute(ClassLoader.getSystemClassLoader(), parentCLAttrKey)
//        attributes[partitionLoadersAttrKey] = MutableObjectContainerAttribute(partitionLoadersAttrKey)
//        get(partitionLoadersAttrKey).registerLoaders()
//    }
//
//    override val parent: ExtensionEnvironment? = null
//
//    override fun <T : ExtensionEnvironment.Attribute> find(key: ExtensionEnvironment.Attribute.Key<T>): T? {
//        return attributes[key] as? T
//    }
//
//    override fun <T : ExtensionEnvironment.Attribute> set(attribute: T) {
//        attributes[attribute.key] = attribute
//    }
//
//    override fun contains(key: ExtensionEnvironment.Attribute.Key<*>): Boolean {
//        return attributes.containsKey(key)
//    }
//
//    override fun remove(key: ExtensionEnvironment.Attribute.Key<*>) {
//        attributes.remove(key)
//    }
//
//    override fun compose(id: String): ExtensionEnvironment {
//        return DefaultExtensionEnvironment(this, id)
//    }
//}

public class DefaultExtensionEnvironment private constructor(
    override val name: String,
    override val parent: ExtensionEnvironment?,
    private val attributes: MutableMap<ExtensionEnvironment.Attribute.Key<*>, ExtensionEnvironment.Attribute>,
    private val emittedViews: MutableMap<ExtensionEnvironment.Attribute.Key<*>, MutableList<ExtensionEnvironment.Attribute.View<*>>>
) : ExtensionEnvironment {
    public constructor(
        id: String,
    ) : this(id, null, ConcurrentHashMap(), ConcurrentHashMap())

    override fun <T : ExtensionEnvironment.Attribute> find(key: ExtensionEnvironment.Attribute.Key<T>): T? {
        return (attributes[key]?.takeUnless {
            (it is ExtensionEnvironment.Attribute.View<*>) && !it.isValid
        } ?: parent?.find(key)) as? T
    }

    override fun <T : ExtensionEnvironment.Attribute> set(attribute: T) {
        if (attribute is ExtensionEnvironment.Attribute.View<*>) throw IllegalArgumentException(
            "Cannot directly set a view in an ExtensionEnvironment."
        )

        emittedViews[attribute.key]?.forEach { (it as ExtensionEnvironment.Attribute.View<T>).reference = attribute }
        attributes[attribute.key] = attribute
    }

    override fun contains(key: ExtensionEnvironment.Attribute.Key<*>): Boolean {
        return parent?.contains(key) == true || attributes[key]?.takeUnless {
            (it is ExtensionEnvironment.Attribute.View<*>) && !it.isValid
        } != null
    }

    override fun remove(key: ExtensionEnvironment.Attribute.Key<*>) {
        if (attributes[key] !is ExtensionEnvironment.Attribute.View<*>) {
            attributes.remove(key)
            emittedViews[key]?.forEach { it.isValid = false }
        }
    }

    override fun compose(id: String): ExtensionEnvironment {
        val childAttributes = ConcurrentHashMap<ExtensionEnvironment.Attribute.Key<*>, ExtensionEnvironment.Attribute>()

        val composed = DefaultExtensionEnvironment(
            id,
            this,
            childAttributes,
            ConcurrentHashMap()
        )

        val newViews = attributes.values.mapNotNull {
            it.compose(composed)
        }

        newViews.forEach {
            emittedViews.getOrPut(it.key) {
                ArrayList()
            }.add(it)

            childAttributes[it.key] = it
        }

        return composed
    }
}