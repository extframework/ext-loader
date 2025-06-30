package dev.extframework.tooling.api.environment

fun check(condition: Boolean) {
    check(condition) {"Invariant not met"}
}
