package io.github.ehbridge.storage

import io.github.ehbridge.model.*
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipFile

interface Archive : Closeable { val zip: ZipFile }

/** Document identities are opaque. Only the SAF implementation interprets them. */
interface Store {
    fun children(parent: Node): List<Node>
    fun read(file: Node): InputStream
    fun write(file: Node): OutputStream
    fun mkdir(parent: Node, name: String): Node
    fun create(parent: Node, name: String): Node
    fun rename(node: Node, name: String): Node?
    fun delete(node: Node)
    fun archive(node: Node, control: Control): Archive
    fun requireSeparateRoots(input: Node, output: Node)
}

const val BUFFER_SIZE = 128 * 1024
private val copyBuffer = ThreadLocal.withInitial { ByteArray(BUFFER_SIZE) }

fun copyBytes(input: InputStream, output: OutputStream, control: Control, maxBytes: Long = Long.MAX_VALUE): Long {
    val buffer = copyBuffer.get()
    var count = 0L
    while (true) {
        control.check()
        val read = input.read(buffer)
        if (read < 0) break
        if (read == 0) continue
        if (count > maxBytes - read) throw BridgeFailure(Issue(Code.ARCHIVE_UNSAFE))
        output.write(buffer, 0, read)
        count += read
    }
    return count
}

inline fun <T> atFile(name: String, action: () -> T): T = try { action() }
catch (e: java.util.concurrent.CancellationException) { throw e }
catch (e: BridgeFailure) { throw e }
catch (_: Exception) { throw BridgeFailure(Issue(Code.READ_WRITE_FAILURE, name)) }

fun Store.copyFile(source: Node, destination: Node, control: Control) = atFile(source.name) {
    read(source).use { input -> write(destination).use { output -> copyBytes(input, output, control) } }
}

fun Store.find(parent: Node, name: String): Node? = children(parent).firstOrNull { it.name == name }

fun Store.copyTree(source: Node, target: Node, control: Control, onFile: (String) -> Unit = {}) {
    // Iterative traversal avoids stack overflows with provider-defined directory depths.
    val queue = ArrayDeque<Pair<Node, Node>>()
    val visited = HashSet<String>()
    queue.add(source to target)
    while (queue.isNotEmpty()) {
        control.check()
        val (from, to) = queue.removeFirst()
        if (!visited.add(from.id)) throw BridgeFailure(Issue(Code.READ_WRITE_FAILURE, from.name))
        for (child in atFile(from.name) { children(from) }) {
            control.check()
            if (child.directory) queue.add(child to atFile(child.name) { mkdir(to, child.name) })
            else {
                copyFile(child, atFile(child.name) { create(to, child.name) }, control)
                onFile(child.name)
            }
        }
    }
}
