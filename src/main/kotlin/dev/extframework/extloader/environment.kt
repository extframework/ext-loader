package dev.extframework.extloader

import dev.extframework.boot.dependency.DependencyTypeContainer
import dev.extframework.extloader.environment.registerLoaders
import dev.extframework.tooling.api.environment.*
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

public class RootExtensionEnvironment private constructor(override val name: String = "root") : ExtensionEnvironment {
    private val attributes: MutableMap<ExtensionEnvironment.Attribute.Key<*>, ExtensionEnvironment.Attribute> =
        ConcurrentHashMap()

    public val workingDir: Path
        get() = get(wrkDirAttrKey).value
    public val dependencyTypes: DependencyTypeContainer
        get() = get(dependencyTypesAttrKey).container

    public constructor(
        name: String,
        workingDir: Path,
        dependencyTypes: DependencyTypeContainer,
    ) : this(name) {
        attributes[wrkDirAttrKey] = (ValueAttribute(workingDir, wrkDirAttrKey))
        attributes[dependencyTypesAttrKey] = (DependencyTypeContainerAttribute(dependencyTypes))
        attributes[parentCLAttrKey] = ValueAttribute(ClassLoader.getSystemClassLoader(), parentCLAttrKey)
        attributes[partitionLoadersAttrKey] = MutableObjectContainerAttribute(partitionLoadersAttrKey)
        get(partitionLoadersAttrKey).registerLoaders()
    }

    override val parent: ExtensionEnvironment? = null

    override fun <T : ExtensionEnvironment.Attribute> find(key: ExtensionEnvironment.Attribute.Key<T>): T? {
        return attributes[key] as? T
    }

    override fun <T : ExtensionEnvironment.Attribute> set(attribute: T) {
        attributes[attribute.key] = attribute
    }

    override fun contains(key: ExtensionEnvironment.Attribute.Key<*>): Boolean {
        return attributes.containsKey(key)
    }

    override fun remove(key: ExtensionEnvironment.Attribute.Key<*>) {
        attributes.remove(key)
    }

    override fun compose(name: String): ExtensionEnvironment {
        return ChildExtensionEnvironment(this, name)
    }
}

public class ChildExtensionEnvironment(
    override val parent: ExtensionEnvironment, override val name: String
) : ExtensionEnvironment {
    private val attributes: MutableMap<ExtensionEnvironment.Attribute.Key<*>, ExtensionEnvironment.Attribute> =
        ConcurrentHashMap()

    override fun <T : ExtensionEnvironment.Attribute> find(key: ExtensionEnvironment.Attribute.Key<T>): T? {
        return parent.find(key) ?: attributes[key] as? T
    }

    override fun <T : ExtensionEnvironment.Attribute> set(attribute: T) {
        attributes[attribute.key] = attribute
    }

    override fun contains(key: ExtensionEnvironment.Attribute.Key<*>): Boolean {
        return parent.contains(key) || attributes.containsKey(key)
    }

    override fun remove(key: ExtensionEnvironment.Attribute.Key<*>) {
        if (parent.contains(key)) {
            parent.remove(key)
        } else {
            attributes.remove(key)
        }
    }

    override fun compose(name: String): ExtensionEnvironment {
        return ChildExtensionEnvironment(this, name)
    }
}