package io.github.ehbridge

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import java.io.File
import java.io.FileNotFoundException

/** Debug-only SAF provider. Never included in the release APK. */
class FixtureDocumentsProvider : DocumentsProvider() {
    private val base get() = File(context!!.filesDir, "fixtures").apply { mkdirs() }
    override fun onCreate() = true
    private fun file(id: String): File {
        val result = if (id == "root") base else File(base, id.removePrefix("root/"))
        if (result.canonicalPath != base.canonicalPath && !result.canonicalPath.startsWith(base.canonicalPath + "/")) throw FileNotFoundException()
        return result
    }
    private fun id(file: File) = if (file == base) "root" else "root/" + file.relativeTo(base).invariantSeparatorsPath
    private fun columns(projection: Array<out String>?) = projection ?: arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE, Document.COLUMN_FLAGS)
    private fun add(cursor: MatrixCursor, file: File) {
        cursor.newRow().apply {
            for (column in cursor.columnNames) add(when (column) {
                Document.COLUMN_DOCUMENT_ID -> id(file)
                Document.COLUMN_DISPLAY_NAME -> file.name
                Document.COLUMN_MIME_TYPE -> if (file.isDirectory) Document.MIME_TYPE_DIR else "application/octet-stream"
                Document.COLUMN_SIZE -> file.length()
                Document.COLUMN_FLAGS -> Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_RENAME or (if (file.isDirectory) Document.FLAG_DIR_SUPPORTS_CREATE else Document.FLAG_SUPPORTS_WRITE)
                else -> null
            })
        }
    }
    override fun queryRoots(projection: Array<out String>?): Cursor = MatrixCursor(projection ?: arrayOf(Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE, Root.COLUMN_FLAGS)).apply {
        newRow().apply { for (column in columnNames) add(when (column) { Root.COLUMN_ROOT_ID -> "fixtures"; Root.COLUMN_DOCUMENT_ID -> "root"; Root.COLUMN_TITLE -> "EhBridge fixtures"; Root.COLUMN_FLAGS -> Root.FLAG_SUPPORTS_CREATE or Root.FLAG_SUPPORTS_IS_CHILD; else -> null }) }
    }
    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor = MatrixCursor(columns(projection)).apply { add(this, file(documentId).also { if (!it.exists()) throw FileNotFoundException() }) }
    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor = MatrixCursor(columns(projection)).apply { file(parentDocumentId).listFiles()?.forEach { add(this, it) } }
    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor = ParcelFileDescriptor.open(file(documentId), ParcelFileDescriptor.parseMode(mode))
    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        val result = File(file(parentDocumentId), displayName)
        val created = if (mimeType == Document.MIME_TYPE_DIR) result.mkdir() else result.createNewFile()
        if (!created) throw FileNotFoundException()
        return id(result)
    }
    override fun renameDocument(documentId: String, displayName: String): String {
        val from = file(documentId)
        val to = File(from.parentFile, displayName)
        if (to.exists() || !from.renameTo(to)) throw FileNotFoundException()
        return id(to)
    }
    override fun deleteDocument(documentId: String) { if (!file(documentId).deleteRecursively()) throw FileNotFoundException() }
    override fun isChildDocument(parentDocumentId: String, documentId: String) = documentId.startsWith("$parentDocumentId/")
}
