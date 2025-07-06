//package com.kaolinmc.tooling.api.environment
//
//import com.kaolinmc.extloader.RootExtensionEnvironment
//import kotlin.test.Test
//
//class TestExtensionEnvironment {
//    data class BasicAttribute(
//        val firstString: String = "first one",
//        var basicString: String = "This is a string"
//    ) : ExtensionEnvironment.Attribute {
//
//        init {
//            println("Constructed")
//        }
//
//        companion object : ExtensionEnvironment.Attribute.Key<BasicAttribute>
//
//        override val key: ExtensionEnvironment.Attribute.Key<*> = BasicAttribute
//    }
//
//    class BasicEnv(
//        override val parent: ExtensionEnvironment?,
//        override val name: String
//    ) : ExtensionEnvironment {
//
//    }
//
//    @Test
//    fun `Test set and get works works`() {
//        val env = RootExtensionEnvironment()
//        env.set(BasicAttribute())
//
//        println("Getting value now")
//        println(env[BasicAttribute])
//    }
//
//    @Test
//    fun `Test updates work`() {
//        val env = ExtensionEnvironment()
//
//        env.update(BasicAttribute) { old ->
//            old.basicString = "This value was updated"
//            old.copy(firstString = "Second string")
//        }
//
//        env.set(BasicAttribute())
//
//        println("Getting value now")
//        check(env[BasicAttribute].extract().basicString == "This value was updated")
//        check(env[BasicAttribute].extract().firstString == "Second string")
//    }
//}