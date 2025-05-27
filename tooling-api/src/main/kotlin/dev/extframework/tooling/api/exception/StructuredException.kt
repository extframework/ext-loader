package dev.extframework.tooling.api.exception

public class StructuredException (
    public val type: ExceptionType,
    override val cause: Throwable? = null,
    override val message: String? = null,
    configure: ExceptionConfiguration.() -> Unit = {}
) : Exception() {
    public val context: Map<String, Any>
    public val solutions: List<String>
    public val rootType: ExceptionType by lazy {
        (cause as? StructuredException)?.rootType ?: type
    }

    init {
        val context: MutableMap<String, Any> = LinkedHashMap()
        val solutions: MutableList<String> = ArrayList()

        val scope = object : ExceptionConfiguration {
            override fun Any?.asContext(name: String) {
                if (this@asContext == null) return
                context[name] = this@asContext
            }

            override fun solution(message: String) {
                solutions.add(message)
            }
        }

        scope.configure()

        this.context = context
        this.solutions = solutions
    }

    public inner class Formatted(
        override val message: String,
    ) : Exception()
}

public interface ExceptionConfiguration {
    public infix fun Any?.asContext(name: String)

    public fun solution(message: String)
}