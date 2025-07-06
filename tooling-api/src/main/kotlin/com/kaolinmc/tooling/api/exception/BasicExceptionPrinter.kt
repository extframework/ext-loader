package com.kaolinmc.tooling.api.exception

import java.io.PrintWriter

public open class BasicExceptionPrinter : StackTracePrinter {
    override fun printStacktrace(throwable: Throwable, printer: PrintWriter) {
        printStacktrace(
            printer,
            throwable,
            arrayOf(),
            "",
            ""
        )
    }

    protected open fun filterElement(element: StackTraceElement) : Boolean {
        return !element.className.startsWith("com.durganmcbroom.jobs")
    }

    protected open fun printStacktrace(
        s: PrintWriter,
        throwable: Throwable,
        enclosingTrace: Array<StackTraceElement>,
        caption: String,
        prefix: String,
    ) {
        // Compute number of frames in common between this and enclosing trace
        val trace: Array<StackTraceElement> = throwable.stackTrace
        var m = trace.size - 1
        var n = enclosingTrace.size - 1
        while (m >= 0 && n >= 0 && trace[m] == enclosingTrace[n]) {
            m--
            n--
        }
        val framesInCommon = trace.size - 1 - m

        // Print our stack trace
        s.println(prefix + caption + throwable)

        for (i in 0..m) {
            val element = trace[i]
            if (filterElement(element)) s.println("$prefix\tat $element")
        }
        if (framesInCommon != 0) s.println("$prefix\t... $framesInCommon more")

        // TODO
        // Print suppressed exceptions, if any
//        for (se in throwable.suppressed) printStacktrace(
//            s, se, trace, "Suppressed by: ",
//            prefix + "\t"
//        )

        // Print cause, if any
        val ourCause: Throwable? = throwable.cause
        if (ourCause != null) printStacktrace(
            s,
            ourCause,
            trace,
            "Caused by: ",
            prefix,
        )
    }
}