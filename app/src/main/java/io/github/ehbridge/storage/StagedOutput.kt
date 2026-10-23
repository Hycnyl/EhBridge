package io.github.ehbridge.storage

import io.github.ehbridge.model.*
import java.util.UUID

class StagedOutput(private val store: Store, private val output: Node, private val name: String) {
    var staging: Node? = null
        private set
    private var fallback: Node? = null
    private var committed = false
    fun begin(): Node {
        if (store.find(output, name) != null) throw BridgeFailure(Issue(Code.OUTPUT_EXISTS, name))
        return store.mkdir(output, ".ehbridge-${UUID.randomUUID()}.partial").also { staging = it }
    }
    fun commit(control: Control) {
        control.check()
        if (store.find(output, name) != null) throw BridgeFailure(Issue(Code.OUTPUT_EXISTS, name))
        val renamed = store.rename(staging!!, name)
        if (renamed != null) {
            staging = renamed
            if (renamed.name != name) throw BridgeFailure(Issue(Code.READ_WRITE_FAILURE, name))
            committed = true
            return
        }
        // Providers without rename get an explicit incomplete marker and a deterministic copy/close/remove-marker commit.
        val final = store.mkdir(output, name).also { fallback = it }
        val marker = store.create(final, ".ehbridge-incomplete")
        store.write(marker).use { it.write("EhBridge: incomplete output; safe to remove after task stops.".toByteArray()) }
        store.copyTree(staging!!, final, control)
        control.check()
        store.delete(marker)
        committed = true
        runCatching { store.delete(staging!!) }.onSuccess { staging = null }
    }
    fun cleanup(): List<Issue> {
        val warnings = mutableListOf<Issue>()
        if (!committed) {
            fallback?.let { node -> runCatching { store.delete(node) }.onFailure { warnings.add(Issue(Code.STAGING_CLEANUP_FAILED, node.name)) } }
            staging?.let { node -> runCatching { store.delete(node) }.onFailure { warnings.add(Issue(Code.STAGING_CLEANUP_FAILED, node.name)) } }
        } else if (fallback != null && staging != null) {
            warnings.add(Issue(Code.STAGING_CLEANUP_FAILED, staging!!.name))
        }
        return warnings
    }
}
