package com.hycnyl.ehbridge.scanner

import com.hycnyl.ehbridge.model.*

object Names {
    private val gallery = Regex("^([0-9]+)-(.+)$", RegexOption.DOT_MATCHES_ALL)
    private val page = Regex("^([0-9]{8,})\\.([^/\\\\]+)$", RegexOption.DOT_MATCHES_ALL)
    private val cover = Regex("^thumb(?:\\.[^/\\\\]+)?$", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val junk = setOf(".nomedia", ".ds_store", "thumbs.db", "desktop.ini")
    fun gid(name: String): Long? = gallery.matchEntire(name)?.groupValues?.get(1)?.toLongOrNull()?.takeIf { it > 0 }
    fun cover(name: String) = cover.matches(name)
    fun junk(name: String) = name.lowercase(java.util.Locale.ROOT) in junk
    fun page(name: String, node: Node? = null): Page? {
        val match = page.matchEntire(name) ?: return null
        val digits = match.groupValues[1].trimStart('0').ifEmpty { "0" }
        if (digits == "0") return null
        if (digits.length > 6 || digits.toInt() > 100_000) throw BridgeFailure(Issue(Code.UNSAFE_PAGE_INDEX, name))
        return Page(name, digits.toInt(), node)
    }
    val pageOrder = compareBy<Page> { it.number }.thenBy { it.name }
    fun requireSafeLeaf(name: String) {
        if (name.isEmpty() || name == "." || name == ".." || name.any { it == '/' || it == '\\' || it == '\u0000' } || Regex("^[a-zA-Z]:").containsMatchIn(name))
            throw BridgeFailure(Issue(Code.ARCHIVE_UNSAFE, name.substringAfterLast('/').take(180)))
    }
}
