package com.kaolinmc.tooling.api.exception

import com.kaolinmc.tooling.api.environment.ExtensionEnvironment
import java.io.PrintWriter

public interface StackTracePrinter : ExtensionEnvironment.Attribute {
    override val key: ExtensionEnvironment.Attribute.Key<*>
        get() = StackTracePrinter
    public fun printStacktrace(throwable: Throwable, printer: PrintWriter)

    public companion object : ExtensionEnvironment.Attribute.Key<StackTracePrinter>
}