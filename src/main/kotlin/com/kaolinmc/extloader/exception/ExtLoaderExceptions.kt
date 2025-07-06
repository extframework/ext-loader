package com.kaolinmc.extloader.exception

import com.kaolinmc.tooling.api.exception.ExceptionType

public enum class ExtLoaderExceptions : ExceptionType {
    ExtensionLoadException,
    InvalidErm,
    ExtensionNotUnloadable,
    ExtensionNotFound,
    ExtensionCacheException
}