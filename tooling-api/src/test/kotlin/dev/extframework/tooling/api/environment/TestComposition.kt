package dev.extframework.tooling.api.environment

import dev.extframework.extloader.DefaultExtensionEnvironment
import kotlin.test.Test

class TestComposition {
    open class IntAttribute(
        open var int: Int
    ) : ExtensionEnvironment.Attribute {
        override val key: ExtensionEnvironment.Attribute.Key<*> = IntAttribute

        companion object : ExtensionEnvironment.Attribute.Key<IntAttribute>

        override fun compose(into: ExtensionEnvironment): ExtensionEnvironment.Attribute.View<*>? {
            return View(this)
        }

        private class View(
            override var reference: IntAttribute
        ) : IntAttribute(reference.int), ExtensionEnvironment.Attribute.View<IntAttribute> {
            override var isValid: Boolean = true

            override val key: ExtensionEnvironment.Attribute.Key<*> = IntAttribute

            private var delta: Int = 0

            override var int: Int
                get() = reference.int + delta
                set(value) {
                    delta = value - int
                }
        }
    }

    @Test
    fun `Test read view`() {
        val environment = DefaultExtensionEnvironment("test")

        environment += IntAttribute(6)

        // Test that putting and reading outputs expected
        check(environment[IntAttribute].int == 6)

        val child = environment.compose("test-child")

        // Test that child is able to read appropriately
        check(child[IntAttribute].int == 6)
        // Test that child modification does not affect parent
        child[IntAttribute].int += 1
        check(child[IntAttribute].int == 7)
        check(environment[IntAttribute].int == 6)
        // Test that parent modification does affect child
        environment[IntAttribute].int += 1
        check(environment[IntAttribute].int == 7)
        check(child[IntAttribute].int == 8)
    }

    @Test
    fun `Test put view`() {
        val environment = DefaultExtensionEnvironment("test")

        environment += IntAttribute(6)

        val child = environment.compose("test-child")
        child[IntAttribute].int += 1

        // Test that overwriting updates references and maintains state
        environment += IntAttribute(5)
        check(child[IntAttribute].int == 6)
    }

    @Test
    fun `Test delete view`() {
        val environment = DefaultExtensionEnvironment("test")

        environment += IntAttribute(6)

        val child = environment.compose("test-child")

        // Test deletion does not affect view (important so that local mutable references cannot be made globally mutable)
        child[IntAttribute].int = 7
        child.remove(IntAttribute)
        check(child[IntAttribute].int == 7)

        val attr = child[IntAttribute] as ExtensionEnvironment.Attribute.View<IntAttribute>
        environment.remove(IntAttribute)
        check(child.find(IntAttribute) == null)
        check(!child.contains(IntAttribute))
        check(!attr.isValid)
    }

    @Test
    fun `Test multiple composition read`() {
        val environment = DefaultExtensionEnvironment("test")
        environment += IntAttribute(6)

        val child1 = environment.compose("test-child1")
        val child2 = child1.compose("test-child2")

        child1[IntAttribute].int += 1
        check(child1[IntAttribute].int == 7)
        check(child2[IntAttribute].int == 7)

        child2[IntAttribute].int += 1
        check(child1[IntAttribute].int == 7)
        check(child2[IntAttribute].int == 8)
    }

    @Test
    fun `Test set attribute composition`() {
        val environment = DefaultExtensionEnvironment("test")
        environment += MutableSetAttribute<String>("test-set")

        val key = MutableSetAttribute.Key<String>("test-set")
        environment[key].add("David")
        environment[key].add("Nancy")

        val child = environment.compose("test-child")

        check(child[key].containsAll(listOf("David", "Nancy")))

        child[key].add("Bob")
        check(child[key].containsAll(listOf("David", "Nancy", "Bob")))

        environment[key].add("Georgia")
        check(child[key].containsAll(listOf("David", "Nancy", "Bob", "Georgia")))
    }
}