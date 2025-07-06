package com.kaolinmc.tooling.api.tweaker

import com.kaolinmc.tooling.api.environment.ExtensionEnvironment

/**
 * Its very important that this class is completely stateless.
 */
public interface EnvironmentTweaker {
    public fun tweak(environment: ExtensionEnvironment)
}