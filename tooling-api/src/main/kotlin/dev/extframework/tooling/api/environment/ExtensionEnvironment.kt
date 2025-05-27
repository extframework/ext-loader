@file:JvmName("EnvironmentComposition")

package dev.extframework.tooling.api.environment

//public fun interface EnvironmentAttributeUpdater<T : ExtensionEnvironment.Attribute> : (T) -> T

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

//    {
//        val initial = attributes[key] as? T ?: return null
//
//        val updated = (updates[key] ?: listOf()).fold(initial) { acc, it ->
//            (it as EnvironmentAttributeUpdater<T>).invoke(acc)
//        }
//        attributes[key] = updated
//        updates[key]?.clear()
//
//        return updated
//    }

//    // Performs a lazily evaluated update on the given key, the update only happens once.
//    public fun <T : EnvironmentAttribute> update(
//        key: EnvironmentAttributeKey<T>,
//        updater: EnvironmentAttributeUpdater<T>
//    ) {
//        (updates[key] ?: ConcurrentLinkedQueue<EnvironmentAttributeUpdater<*>>().also { updates.put(key, it) }).apply {
//            add(updater)
//        }
//    }

    public fun <T : Attribute> set(attribute: T)

    public operator fun plusAssign(attribute: Attribute) {
        set(attribute)
    }

//    {
//        attributes[attribute.key] = attribute
//    }

    public fun contains(key: Attribute.Key<*>): Boolean


//    public fun <T : EnvironmentAttribute> setUnless(attribute: T)

//    {
//        if (!attributes.containsKey(attribute.key)) {
//            set(attribute)
//        }
//    }

//    public operator fun <T : EnvironmentAttribute> plusAssign(attribute: T) {
//        set(attribute)
//    }

//    public operator fun plusAssign(other: ExtensionEnvironment) {
//        other.attributes.forEach { (_, attribute) ->
//            updates[attribute.key]?.forEach {
//                (it as EnvironmentAttributeUpdater<EnvironmentAttribute>)(attribute)
//            }
//        }
//
//        attributes.putAll(other.attributes)
//    }

    public fun remove(key: Attribute.Key<*>)
//    {
//        updates.remove(key)
//        attributes.remove(key)
//    }

    public fun compose(name: String) : ExtensionEnvironment

    public interface Attribute {
        public val key: Key<*>

        public interface Key<T : Attribute>

    }
}

