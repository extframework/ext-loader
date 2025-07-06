package com.kaolinmc.tooling.api.exception

public interface ExceptionContextSerializer<T> {
    public val type: Class<T>

    public fun serialize(value: T, helper: Helper): String

    public interface Helper {
        public fun serialize(value: Any) : String
    }
}