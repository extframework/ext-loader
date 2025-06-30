package dev.extframework.tooling.api.environment

import dev.extframework.`object`.ObjectContainer

public open class ObjectContainerView<T : ObjectContainer.IDed> private constructor(
    private val _reference: () -> ObjectContainer<T>,
    private val delta: MutableMap<String, T> = HashMap(),
) : ObjectContainer<T>, Map<String, T> by MapView({
    _reference()
}, delta) {
    public constructor(reference: () -> ObjectContainer<T>) : this(reference, HashMap())

    override fun register(obj: T): Boolean {
        val contained = contains(obj.id)
        delta.put(obj.id, obj)
        return !contained
    }
}

public open class MapView<K, V> internal constructor(
    private val _reference: () -> Map<K, V>,
    private val delta: MutableMap<K, V>,
) : MutableMap<K, V> by delta {
    protected val reference: Map<K, V>
        get() = _reference()

    public constructor(reference: () -> Map<K, V>) : this(reference, HashMap())

    override val keys: MutableSet<K>
        get() = (delta.keys + reference.keys).toMutableSet()
    override val values: MutableCollection<V>
        get() = (delta.values + reference.values).toMutableSet()
    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = (delta.entries + reference.entries.map { it: Map.Entry<K, V> ->
            object : MutableMap.MutableEntry<K, V> {
                override fun setValue(newValue: V): V {
                    return newValue
                }

                override val key: K by it::key
                override val value: V by it::value
            }
        }).toMutableSet()

    override val size: Int
        get() = keys.size

    override fun isEmpty(): Boolean {
        return delta.isEmpty() && reference.isEmpty()
    }

    override fun containsKey(key: K): Boolean {
        return keys.contains(key)
    }

    override fun containsValue(value: V): Boolean {
        return values.contains(value)
    }

    override fun get(key: K): V? {
        return delta[key] ?: reference[key]
    }
}

public open class SetView<T> private constructor(
    private val _reference: () -> Set<T>,
    private val delta: MutableSet<T>,
) : MutableSet<T> by delta {
    protected val reference: Set<T>
        get() = _reference()

    public constructor(reference: () -> Set<T>) : this(reference, HashSet())

    override fun iterator(): MutableIterator<T> = object : MutableIterator<T> {
        val deltaIterator = delta.iterator()
        val referenceIterator = reference.iterator()

        override fun remove() {
            deltaIterator.remove()
        }

        override fun next(): T {
            return if (deltaIterator.hasNext()) {
                deltaIterator.next()
            } else {
                referenceIterator.next()
            }
        }

        override fun hasNext(): Boolean {
            return deltaIterator.hasNext() || referenceIterator.hasNext()
        }
    }

    override val size: Int
        get() = reference.size + delta.size

    override fun isEmpty(): Boolean {
        return reference.isEmpty() || delta.isEmpty()
    }

    override fun contains(element: T): Boolean {
        return reference.contains(element) || delta.contains(element)
    }

    override fun containsAll(elements: Collection<T>): Boolean {
        return (reference + delta).containsAll(elements)
    }
}

// TODO this list will not hold to invariants (specifically on writing/removing from indices)
public open class ListView<T> private constructor(
    private val _reference: () -> List<T>,
    private val delta: MutableList<T>,
) : MutableList<T> by delta {
    protected val reference: List<T>
        get() = _reference()

    public constructor(reference: () -> List<T>) : this(reference, ArrayList())

    override fun listIterator(): MutableListIterator<T> =
        listIterator(delta.listIterator(), reference.listIterator())

    override fun listIterator(index: Int): MutableListIterator<T> {
        val iterator = listIterator()
        // This kind of destroys the purpose of this method
        repeat(index) { iterator.next() }

        return iterator
    }

    override fun subList(fromIndex: Int, toIndex: Int): MutableList<T> {
        return (delta + reference).subList(fromIndex, toIndex).toMutableList()
    }

    override val size: Int
        get() = delta.size + reference.size

    override fun isEmpty(): Boolean {
        return delta.isEmpty() && reference.isEmpty()
    }

    override fun contains(element: T): Boolean {
        return delta.contains(element) || reference.contains(element)
    }

    override fun containsAll(elements: Collection<T>): Boolean {
        return (delta + reference).containsAll(elements)
    }

    override fun get(index: Int): T {
        return (delta + reference)[index]
    }

    override fun indexOf(element: T): Int {
        return (delta + reference).indexOf(element)
    }

    override fun lastIndexOf(element: T): Int {
        return (delta + reference).lastIndexOf(element)
    }

    override fun iterator(): MutableIterator<T> {
        return listIterator()
    }

    private fun listIterator(
        deltaIterator: MutableListIterator<T>,
        referenceIterator: ListIterator<T>,
    ): MutableListIterator<T> {
        return object : MutableListIterator<T> by deltaIterator {
            override fun next(): T {
                return if (deltaIterator.hasNext()) {
                    deltaIterator.next()
                } else referenceIterator.next()
            }

            override fun hasNext(): Boolean {
                return deltaIterator.hasNext() || referenceIterator.hasNext()
            }

            override fun hasPrevious(): Boolean {
                return deltaIterator.hasPrevious() || referenceIterator.hasPrevious()
            }

            override fun previous(): T {
                return if (referenceIterator.hasPrevious()) {
                    referenceIterator.previous()
                } else deltaIterator.previous()
            }

            override fun nextIndex(): Int {
                return if (deltaIterator.hasNext()) {
                    deltaIterator.nextIndex()
                } else referenceIterator.nextIndex()
            }

            override fun previousIndex(): Int {
                return if (referenceIterator.hasPrevious()) {
                    referenceIterator.previousIndex()
                } else deltaIterator.previousIndex()
            }
        }
    }
}