package dev.extframework.extloader.exception

import dev.extframework.tooling.api.exception.ExceptionType

public enum class ExtLoaderExceptions : ExceptionType {
    ExtensionLoadException,
    InvalidErm,
    ExtensionNotUnloadable,
    ExtensionNotFound,
    ExtensionCacheException
}