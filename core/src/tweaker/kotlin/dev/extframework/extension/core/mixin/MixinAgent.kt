package dev.extframework.extension.core.mixin

import org.objectweb.asm.tree.ClassNode

public interface MixinAgent {
    public fun transformClass(name: String, node: ClassNode?) : ClassNode?
}