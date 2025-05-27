//package dev.extframework.tooling.api.environment
//
//import kotlin.reflect.KProperty
//
//public interface DeferredValue<T> {
//    public val name: String
//
//    public fun get(): Result<T>
//
//    public fun listen(listener: (T) -> Unit)
//
//    public operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
//        return extract()
//    }
//}
//
//public inline fun <T> defer(name: String, crossinline provider: () -> T?): DeferredValue<T> {
//    return object : DeferredValue<T> {
//        private var value: T? = null
//        private val listeners = ArrayList<(T) -> Unit>()
//        override val name: String
//            get() = name
//
//        override fun get(): Result<T> {
//            if (value == null) {
//                value = provider()
//            }
//
//            if (value != null) {
//                val nonNullValue = value!!
//                listeners.removeAll {
//                    it(nonNullValue)
//                    true
//                }
//
//                return Result.success(nonNullValue)
//            }
//
//            return Result.failure(UninitializedValueException(name))
//        }
//
//        override fun listen(listener: (T) -> Unit) {
//            if (value != null) listener(value!!)
//            else listeners.add(listener)
//        }
//    }
//}
//
//public inline fun <T, V> DeferredValue<T>.map(crossinline transformer: (T) -> V): DeferredValue<V> {
//    return defer(name) {
//        get().map(transformer).getOrNull()
//    }
//}
//
//public fun <T> DeferredValue<T>.getOrNull(): T? = get().getOrNull()
//
//public fun <T> DeferredValue<T>.extract(): T = get().getOrThrow()