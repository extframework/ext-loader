package com.kaolinmc.extloader.util

public infix fun <A: Any, B: Any> A?.toOrNull(b: B?): Pair<A, B>? = this?.let { a -> b?.let { a to it } }