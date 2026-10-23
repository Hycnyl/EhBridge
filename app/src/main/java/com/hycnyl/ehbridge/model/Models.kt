package com.hycnyl.ehbridge.model

import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

enum class Layout { LOOSE, CBZ, INVALID }
enum class Operation { COPY, PACK, UNPACK, SKIP }
enum class Outcome { COPIED, CONVERTED, SKIPPED, FAILED }
enum class Code {
    INVALID_GALLERY_ITEM, MISSING_REQUIRED_FILE, NO_PAGE_FILES, LAYOUT_AMBIGUOUS,
    UNMAPPED_EXTRA_DATA, GID_MISMATCH, ARCHIVE_UNSAFE, UNSAFE_PAGE_INDEX,
    OUTPUT_EXISTS, READ_WRITE_FAILURE, SPIDER_CODEC_ERROR, ROOTS_OVERLAP,
    ROOT_RELATION_UNKNOWN, PERMISSION_REQUIRED, CANCELLED, SOURCE_CHANGED,
    TOKEN_UNAVAILABLE, TOKEN_SOURCE_CONFLICT, COMICINFO_DIFFERENT,
    GALLERY_INCOMPLETE, SOURCE_STATE_NOT_TRANSFERRED, STAGING_CLEANUP_FAILED,
    XML_METADATA_LIMITED, MISSING_COMICINFO, MISSING_COVER, METADATA_REPAIRED,
    MISSING_EHVIEWER
}
data class Issue(val code: Code, val file: String = "")
class BridgeFailure(val issue: Issue) : Exception(issue.code.name)
data class Node(val id: String, val name: String, val directory: Boolean, val size: Long = -1)
data class Page(val name: String, val number: Int, val node: Node? = null)
data class Plan(
    val source: Node,
    val gid: Long? = null,
    val layout: Layout = Layout.INVALID,
    val operation: Operation = Operation.SKIP,
    val pages: List<Page> = emptyList(),
    val xml: Node? = null,
    val cover: Node? = null,
    val archive: Node? = null,
    val issues: List<Issue> = emptyList(),
    val warnings: List<Issue> = emptyList()
)
data class Result(val name: String, val outcome: Outcome, val issues: List<Issue> = emptyList(), val warnings: List<Issue> = emptyList(), val sourceId: String = "")
data class Progress(val completed: Int = 0, val total: Int = 0, val name: String = "", val filesDone: Int = 0, val filesTotal: Int = 0)
class Control {
    private val cancelled = AtomicBoolean(false)
    fun cancel() { cancelled.set(true) }
    fun check() { if (cancelled.get() || Thread.currentThread().isInterrupted) throw CancellationException() }
    val isCancelled get() = cancelled.get()
}
