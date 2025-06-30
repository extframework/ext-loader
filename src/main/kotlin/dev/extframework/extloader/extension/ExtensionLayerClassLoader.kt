package dev.extframework.extloader.extension

public class ExtensionLayerClassLoader(
    parent: ClassLoader,
    private val name: String = "Extension Layer"
) : ClassLoader(
    parent
) {
    override fun toString(): String {
        return name
    }
}