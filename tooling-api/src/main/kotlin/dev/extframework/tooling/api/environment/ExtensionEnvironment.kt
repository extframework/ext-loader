package dev.extframework.tooling.api.environment

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

    public fun compose(id: String) : ExtensionEnvironment

    public interface Attribute {
        public val key: Key<*>

        public fun compose(
            into: ExtensionEnvironment,
        ): View<*>? {
            return null
        }

        public interface Key<T : Attribute>

        public interface View<T : Attribute> : Attribute {
            public var isValid: Boolean
            public var reference: T
        }
    }
}

