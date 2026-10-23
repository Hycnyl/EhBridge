package io.github.ehbridge.ui

import android.content.Context
import io.github.ehbridge.R
import io.github.ehbridge.UiState
import io.github.ehbridge.model.*

fun issueLabel(code: Code): Int = when (code) {
    Code.INVALID_GALLERY_ITEM -> R.string.issue_invalid_gallery_item
    Code.MISSING_REQUIRED_FILE -> R.string.issue_missing_required_file
    Code.NO_PAGE_FILES -> R.string.issue_no_page_files
    Code.LAYOUT_AMBIGUOUS -> R.string.issue_layout_ambiguous
    Code.UNMAPPED_EXTRA_DATA -> R.string.issue_unmapped_extra_data
    Code.GID_MISMATCH -> R.string.issue_gid_mismatch
    Code.ARCHIVE_UNSAFE -> R.string.issue_archive_unsafe
    Code.UNSAFE_PAGE_INDEX -> R.string.issue_unsafe_page_index
    Code.OUTPUT_EXISTS -> R.string.issue_output_exists
    Code.READ_WRITE_FAILURE -> R.string.issue_read_write_failure
    Code.SPIDER_CODEC_ERROR -> R.string.issue_spider_codec_error
    Code.ROOTS_OVERLAP -> R.string.issue_roots_overlap
    Code.ROOT_RELATION_UNKNOWN -> R.string.issue_root_relation_unknown
    Code.PERMISSION_REQUIRED -> R.string.issue_permission_required
    Code.CANCELLED -> R.string.issue_cancelled
    Code.SOURCE_CHANGED -> R.string.issue_source_changed
    Code.TOKEN_UNAVAILABLE -> R.string.issue_token_unavailable
    Code.TOKEN_SOURCE_CONFLICT -> R.string.issue_token_source_conflict
    Code.COMICINFO_DIFFERENT -> R.string.issue_comicinfo_different
    Code.GALLERY_INCOMPLETE -> R.string.issue_gallery_incomplete
    Code.SOURCE_STATE_NOT_TRANSFERRED -> R.string.issue_source_state_not_transferred
    Code.STAGING_CLEANUP_FAILED -> R.string.issue_staging_cleanup_failed
    Code.XML_METADATA_LIMITED -> R.string.issue_xml_metadata_limited
}
fun operationLabel(operation: Operation) = when (operation) {
    Operation.COPY -> R.string.copy
    Operation.PACK -> R.string.pack
    Operation.UNPACK -> R.string.unpack
    Operation.SKIP -> R.string.skip
}
fun outcomeLabel(outcome: Outcome) = when (outcome) {
    Outcome.COPIED -> R.string.copied
    Outcome.CONVERTED -> R.string.converted
    Outcome.SKIPPED -> R.string.skipped
    Outcome.FAILED -> R.string.failed
}
fun layoutLabel(layout: Layout) = when (layout) {
    Layout.LOOSE -> R.string.layout_a
    Layout.CBZ -> R.string.layout_b
    Layout.INVALID -> R.string.layout_invalid
}

fun report(context: Context, state: UiState): String = buildString {
    appendLine(context.getString(R.string.report_title))
    appendLine("EhBridge 1.0.0 | ${state.phase.name}")
    appendLine(context.getString(layoutLabel(state.target)))
    for (plan in state.plans) {
        appendLine()
        appendLine(plan.source.name)
        appendLine(context.getString(R.string.planned_action, context.getString(operationLabel(plan.operation))))
        val result = state.results[plan.source.id]
        appendLine(context.getString(R.string.result, result?.let { context.getString(outcomeLabel(it.outcome)) } ?: context.getString(R.string.pending)))
        for (issue in result?.issues ?: plan.issues) {
            appendLine("[${issue.code}] ${context.getString(issueLabel(issue.code))}")
            if (issue.file.isNotEmpty()) appendLine("  ${issue.file}")
        }
        for (warning in (result?.warnings ?: plan.warnings).distinct()) {
            appendLine("[${warning.code}] ${context.getString(issueLabel(warning.code))}")
            if (warning.file.isNotEmpty()) appendLine("  ${warning.file}")
        }
    }
    state.error?.let { appendLine("[${it.code}] ${context.getString(issueLabel(it.code))}") }
}
