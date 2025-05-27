package dev.extframework.tooling.api.environment

public class MissingEnvironmentKeyException(value: String) : Exception("The key: '$value' has not been registered with this environment.") {
}