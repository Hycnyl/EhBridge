package com.hycnyl.ehbridge.storage

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.system.Os
import android.system.OsConstants
import com.hycnyl.ehbridge.model.*
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.zip.ZipFile

/** Queries each directory once rather than making one provider query per file property. */
class SafStore(private val context: Context) : Store {
    private val resolver = context.contentResolver
    private val projection = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE)

    fun root(tree: Uri): Node = describe(DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)))

    private fun describe(uri: Uri): Node = resolver.query(uri, projection, null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) throw IOException("Document unavailable")
        Node(uri.toString(), cursor.getString(1), cursor.getString(2) == Document.MIME_TYPE_DIR, if (cursor.isNull(3)) -1 else cursor.getLong(3))
    } ?: throw IOException("Document unavailable")

    override fun children(parent: Node): List<Node> {
        val uri = Uri.parse(parent.id)
        val query = DocumentsContract.buildChildDocumentsUriUsingTree(uri, DocumentsContract.getDocumentId(uri))
        return resolver.query(query, projection, null, null, null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val child = DocumentsContract.buildDocumentUriUsingTree(uri, cursor.getString(0))
                    add(Node(child.toString(), cursor.getString(1), cursor.getString(2) == Document.MIME_TYPE_DIR, if (cursor.isNull(3)) -1 else cursor.getLong(3)))
                }
            }
        } ?: throw IOException("Directory unavailable")
    }
    override fun read(file: Node) = atFile(file.name) { resolver.openInputStream(Uri.parse(file.id)) ?: throw IOException("Read unavailable") }
    override fun write(file: Node) = atFile(file.name) { resolver.openOutputStream(Uri.parse(file.id), "wt") ?: throw IOException("Write unavailable") }
    override fun mkdir(parent: Node, name: String) = atFile(name) { createDocument(parent, name, Document.MIME_TYPE_DIR) }
    override fun create(parent: Node, name: String) = atFile(name) { createDocument(parent, name, "application/octet-stream") }

    private fun createDocument(parent: Node, name: String, mime: String): Node {
        require(name.isNotEmpty() && name != "." && name != ".." && '/' !in name && '\u0000' !in name)
        // SAF createDocument must create a new child (it may choose a unique suffixed name).
        // StagedOutput checks final-folder conflicts; avoid relisting a growing directory for every page.
        val uri = DocumentsContract.createDocument(resolver, Uri.parse(parent.id), mime, name) ?: throw IOException("Creation unavailable")
        val node = describe(uri)
        if (node.name != name) {
            // Never accept a provider's silently suffixed or extension-rewritten filename.
            runCatching { DocumentsContract.deleteDocument(resolver, uri) }
            if (find(parent, name) != null) throw BridgeFailure(Issue(Code.OUTPUT_EXISTS, name))
            throw IOException("Provider changed filename")
        }
        return node
    }
    override fun rename(node: Node, name: String): Node? {
        val uri = Uri.parse(node.id)
        val flags = resolver.query(uri, arrayOf(Document.COLUMN_FLAGS), null, null, null)?.use { if (it.moveToFirst()) it.getInt(0) else 0 } ?: 0
        if (flags and Document.FLAG_SUPPORTS_RENAME == 0) return null
        return try { DocumentsContract.renameDocument(resolver, uri, name)?.let(::describe) }
        catch (_: UnsupportedOperationException) { null }
    }
    override fun delete(node: Node) {
        if (!DocumentsContract.deleteDocument(resolver, Uri.parse(node.id))) throw IOException("Deletion unavailable")
    }

    override fun requireSeparateRoots(input: Node, output: Node) {
        val a = Uri.parse(input.id)
        val b = Uri.parse(output.id)
        if (!input.directory || !output.directory) throw BridgeFailure(Issue(Code.PERMISSION_REQUIRED))
        // Different providers can alias the very same local directory (e.g. Downloads vs External Storage).
        // Without a shared ancestry API we cannot safely prove that writing will stay outside the input tree.
        if (a.authority != b.authority) throw BridgeFailure(Issue(Code.ROOT_RELATION_UNKNOWN))
        val external = a.authority == "com.android.externalstorage.documents"
        fun normalized(id: String): String = (if (external && id.startsWith("home:"))
            "primary:Documents/" + id.removePrefix("home:").trimStart('/') else id).trimEnd('/')
        val first = normalized(DocumentsContract.getDocumentId(a))
        val second = normalized(DocumentsContract.getDocumentId(b))
        fun under(child: String, parent: String) = child.startsWith("$parent/") || (parent.endsWith(':') && child.startsWith(parent))
        if (first == second || under(first, second) || under(second, first)) throw BridgeFailure(Issue(Code.ROOTS_OVERLAP))
        if (external && Build.VERSION.SDK_INT < 29) {
            val one = first.lowercase(java.util.Locale.ROOT)
            val two = second.lowercase(java.util.Locale.ROOT)
            if (one == two || under(one, two) || under(two, one)) throw BridgeFailure(Issue(Code.ROOT_RELATION_UNKNOWN))
        }
        try {
            if (Build.VERSION.SDK_INT < 29) throw UnsupportedOperationException()
            if (DocumentsContract.isChildDocument(resolver, a, b) || DocumentsContract.isChildDocument(resolver, b, a))
                throw BridgeFailure(Issue(Code.ROOTS_OVERLAP))
        } catch (e: BridgeFailure) { throw e }
        catch (_: Exception) {
            // External storage IDs have documented hierarchical volume:path semantics.
            if (a.authority != "com.android.externalstorage.documents") throw BridgeFailure(Issue(Code.ROOT_RELATION_UNKNOWN))
        }
    }

    override fun archive(node: Node, control: Control): Archive {
        control.check()
        val descriptor = atFile(node.name) {
            resolver.openFileDescriptor(Uri.parse(node.id), "r") ?: throw IOException("Archive unavailable")
        }
        val seekable = runCatching { Os.lseek(descriptor.fileDescriptor, 0, OsConstants.SEEK_CUR); true }.getOrDefault(false)
        if (seekable) {
            try {
                // This path refers to our already-authorized descriptor; never to Uri.path.
                val zip = ZipFile("/proc/self/fd/${descriptor.fd}")
                return object : Archive {
                    override val zip = zip
                    override fun close() { try { zip.close() } finally { descriptor.close() } }
                }
            } catch (_: IOException) {
                // Seekable SAF descriptors are not necessarily reopenable filesystem paths.
                // Scoped-storage/FUSE and proxy providers can grant the descriptor while
                // denying open("/proc/self/fd/N"). Keep using the authorized descriptor.
            } catch (_: SecurityException) {
                // The provider's descriptor grant still permits the stream fallback below.
            } catch (e: Exception) { descriptor.close(); throw e }
        }
        // ZIP needs random access. Spool only when direct reopening is unavailable; lseek
        // succeeding alone is not evidence that ZipFile can reopen the descriptor path.
        val spool = File(context.cacheDir, "ehbridge-${UUID.randomUUID()}.zip")
        try {
            if (seekable) atFile(node.name) { Os.lseek(descriptor.fileDescriptor, 0, OsConstants.SEEK_SET) }
            atFile(node.name) {
                ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                    spool.outputStream().use { copyBytes(input, it, control, 128L * 1024 * 1024 * 1024) }
                }
            }
            val zip = ZipFile(spool)
            return object : Archive {
                override val zip = zip
                override fun close() { try { zip.close() } finally { spool.delete() } }
            }
        } catch (e: Exception) { runCatching { descriptor.close() }; spool.delete(); throw e }
    }
}
