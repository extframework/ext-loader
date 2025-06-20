package dev.extframework.tooling.api.environment

// TODO redo environment composition: the issue is that mutable attributes,
//   such as the partition loaders attr, can be mutated in the root environment
//   either way.
public interface ExtensionEnvironment {
    public val parent: ExtensionEnvironment?
    public val name: String

    public operator fun <T : Attribute> get(key: Attribute.Key<T>): T {
        return find(key) ?: throw MissingEnvironmentKeyException(
            key.toString()
        )
    }

    public fun <T : Attribute> find(key: Attribute.Key<T>): T?

    public fun <T : Attribute> set(attribute: T)

    public operator fun plusAssign(attribute: Attribute) {
        set(attribute)
    }

    public fun contains(key: Attribute.Key<*>): Boolean


    public fun remove(key: Attribute.Key<*>)

    public fun compose(name: String) : ExtensionEnvironment

    public interface Attribute {
        public val key: Key<*>

        // name = environment name
        public fun compose(): Attribute? {
            return null
        }

        public interface Key<T : Attribute>
    }
}

