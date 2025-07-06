package com.kaolinmc.extloader.util

import com.kaolinmc.archives.ArchiveReference
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URI
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

public fun emptyArchiveReference(
    location: URI = URI("archive:empty")
): ArchiveReference = object : ArchiveReference {
    private val entries: MutableMap<String, () -> ArchiveReference.Entry> = HashMap()
    override val isClosed: Boolean = false
    override val location: URI = location
    override val modified: Boolean = entries.isNotEmpty()
    override val name: String? = null
    override val reader: ArchiveReference.Reader = object : ArchiveReference.Reader {
        override fun entries(): Sequence<ArchiveReference.Entry> {
            return entries.values.asSequence().map { it() }
        }

        override fun of(name: String): ArchiveReference.Entry? {
            return entries[name]?.invoke()
        }
    }
    override val writer = object : ArchiveReference.Writer {
        override fun put(entry: ArchiveReference.Entry) {
            entries[entry.name] = { entry }
        }

        override fun remove(name: String) {
            entries.remove(name)
        }

    }

    override fun close() {}
}


public fun ArchiveReference.copy(): ArchiveReference = object : ArchiveReference by this {
    override var isClosed: Boolean = this@copy.isClosed
        private set
    override var modified: Boolean = false
        private set
    override val reader: ArchiveReference.Reader = CopiedReader(this@copy.reader)
    override val writer: ArchiveReference.Writer = CopiedWriter()

    override fun close() {
        isClosed = true
    }

    private val modifiedEntries = HashMap<String, ArchiveReference.Entry>()
    private val removed = HashSet<String>()

    private fun ensureOpen() {
        check(!isClosed) { "This reference is closed!" }
    }

    private inner class CopiedReader(
        private val delegate: ArchiveReference.Reader
    ) : ArchiveReference.Reader {
        override fun entries(): Sequence<ArchiveReference.Entry> {
            val alreadyRead = HashSet<String>()

            return (modifiedEntries.keys.asSequence() + delegate.entries().map { it.name })
                .filter(alreadyRead::add).mapNotNull(::of)
        }

        override fun of(name: String): ArchiveReference.Entry? {
            ensureOpen()

            return (modifiedEntries[name] ?: delegate[name])?.takeUnless { removed.contains(it.name) }
        }
    }

    private inner class CopiedWriter : ArchiveReference.Writer {
        override fun put(entry: ArchiveReference.Entry) {
            ensureOpen()
            modifiedEntries[entry.name] = entry
            removed.remove(entry.name)
        }

        override fun remove(name: String) {
            ensureOpen()
            removed.add(name)
        }
    }
}


public fun ArchiveReference.slice(path: Path): ArchiveReference {
    val superDelegate = this.copy()
    return object : ArchiveReference by superDelegate {
        override val name: String = "'${this@slice.name ?: "unnamed archive"}' sliced along $path"
        override val reader: ArchiveReference.Reader = SlicedReader(superDelegate.reader)
        override val writer: ArchiveReference.Writer = SlicedWriter(superDelegate.writer)

        private val thisRef = this

        private inner class SlicedReader(
            private val delegate: ArchiveReference.Reader
        ) : ArchiveReference.Reader {
            override fun entries(): Sequence<ArchiveReference.Entry> = delegate.entries().filter {
                it.name.startsWith(path.toString())
            }.map {
                it.copy(
                    name = it.name.removePrefix(path.toString()).removePrefix("/"),
                    handle = thisRef
                )
            }

            override fun of(name: String): ArchiveReference.Entry? {
                val newName = path.resolve(name).toString()

                return delegate[newName]?.copy(
                    name = name,
                    handle = thisRef
                )
            }
        }

        private inner class SlicedWriter(
            private val delegate: ArchiveReference.Writer
        ) : ArchiveReference.Writer {
            override fun put(entry: ArchiveReference.Entry) {
                delegate.put(
                    entry.copy(
                        name = path.resolve(entry.name).toString()
                    )
                )
            }

            override fun remove(name: String) {
                delegate.remove(name)
            }
        }
    }
}

public fun ArchiveReference.toInputStream(): InputStream {
    val out = ByteArrayOutputStream()

    JarOutputStream(out).use { target ->
        reader.entries().forEach { e ->
            val entry = JarEntry(e.name)

            target.putNextEntry(entry)

            val eIn = e.open()

            val buffer = ByteArray(1024)

            while (true) {
                val count: Int = eIn.read(buffer)
                if (count == -1) break

                target.write(buffer, 0, count)
            }

            target.closeEntry()
        }
    }

    return ByteArrayInputStream(out.toByteArray())
}