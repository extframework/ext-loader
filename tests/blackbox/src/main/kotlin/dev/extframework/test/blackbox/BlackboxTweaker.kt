package dev.extframework.test.blackbox

import dev.extframework.tooling.api.environment.ExtensionEnvironment
import dev.extframework.tooling.api.extension.ExtensionNode
import dev.extframework.tooling.api.extension.ExtensionUnloader
import dev.extframework.tooling.api.tweaker.EnvironmentTweaker

public class BlackboxTweaker : EnvironmentTweaker {
    override fun tweak(environment: ExtensionEnvironment) {
        println("Tweaker has been ran.")
        System.setProperty("tweaker", "true")

//        environment += object : ExtensionInitializer {
//            override fun init(nodes: List<ExtensionNode>): Job<Unit> = job {
//                println("Initialization: $nodes")
//                System.setProperty("init", "true")
//            }
//        }

        environment += object : ExtensionUnloader {
            override fun cleanup(nodes: List<ExtensionNode>) {
                println("Cleaning: $nodes")

                environment.remove(ExtensionUnloader)

                System.setProperty("clean", "true")
            }
        }
    }
}