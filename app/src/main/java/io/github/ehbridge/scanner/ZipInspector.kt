package io.github.ehbridge.scanner

import io.github.ehbridge.model.*
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

object ZipInspector {
    const val MAX_ENTRY = 8L * 1024 * 1024 * 1024
    const val MAX_TOTAL = 128L * 1024 * 1024 * 1024
    const val MAX_ENTRIES = 120_000
    fun inspect(zip: ZipFile, control: Control): List<Page> {
        val names = HashSet<String>()
        val pages = ArrayList<Page>()
        var xml = false
        var total = 0L
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            control.check()
            val entry = entries.nextElement()
            val name = entry.name
            if (!names.add(name) || names.size > MAX_ENTRIES) unsafe(name)
            if (entry.method != ZipEntry.STORED && entry.method != ZipEntry.DEFLATED) unsafe(name)
            val leaf = if (entry.isDirectory) name.removeSuffix("/") else name
            Names.requireSafeLeaf(leaf)
            if (entry.size < 0 || entry.size > MAX_ENTRY || entry.compressedSize < 0) unsafe(name)
            if (entry.size > 16 * 1024 * 1024 && entry.size / maxOf(1L, entry.compressedSize) > 1000) unsafe(name)
            if (total > MAX_TOTAL - entry.size) unsafe(name)
            total += entry.size
            if (entry.isDirectory) {
                if (entry.size != 0L) unsafe(name)
                continue
            }
            when {
                name == "ComicInfo.xml" -> xml = true
                Names.page(name) != null -> pages.add(Names.page(name)!!)
                Names.junk(name) || entry.size == 0L -> Unit
                else -> throw BridgeFailure(Issue(Code.UNMAPPED_EXTRA_DATA, name))
            }
        }
        if (!xml) throw BridgeFailure(Issue(Code.MISSING_REQUIRED_FILE, "ComicInfo.xml (CBZ)"))
        if (pages.isEmpty()) throw BridgeFailure(Issue(Code.NO_PAGE_FILES, "CBZ"))
        return pages.sortedWith(Names.pageOrder)
    }
    private fun unsafe(name: String): Nothing = throw BridgeFailure(Issue(Code.ARCHIVE_UNSAFE, name.take(180)))
}
