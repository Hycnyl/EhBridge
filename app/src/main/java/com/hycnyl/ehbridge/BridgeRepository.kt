package com.hycnyl.ehbridge

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import com.hycnyl.ehbridge.converter.BatchConverter
import com.hycnyl.ehbridge.model.*
import com.hycnyl.ehbridge.scanner.GalleryScanner
import com.hycnyl.ehbridge.storage.SafStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.CancellationException

enum class Phase { IDLE, SCANNING, READY, RUNNING, FINISHED, CANCELLED }
enum class ThemeMode { SYSTEM, LIGHT, DARK }
val NON_BLOCKING_CODES: Set<Code> = linkedSetOf(
    Code.MISSING_COMICINFO, Code.MISSING_COVER, Code.METADATA_REPAIRED,
    Code.TOKEN_UNAVAILABLE, Code.TOKEN_SOURCE_CONFLICT, Code.COMICINFO_DIFFERENT,
    Code.GALLERY_INCOMPLETE, Code.SOURCE_STATE_NOT_TRANSFERRED,
    Code.STAGING_CLEANUP_FAILED, Code.XML_METADATA_LIMITED
)
data class Selection(val uri: String, val name: String)
data class UiState(
    val input: Selection? = null,
    val output: Selection? = null,
    val target: Layout = Layout.CBZ,
    val phase: Phase = Phase.IDLE,
    val plans: List<Plan> = emptyList(),
    val results: Map<String, Result> = emptyMap(),
    val progress: Progress = Progress(),
    val error: Issue? = null,
    val language: String = "",
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val enabledWarnings: Set<Code> = NON_BLOCKING_CODES
) {
    val busy get() = phase == Phase.SCANNING || phase == Phase.RUNNING
}

/** Owned by Application, so rotation, locale recreation and Activity removal cannot duplicate work. */
class BridgeRepository(private val context: Context) {
    private val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutable = MutableStateFlow(UiState(
        input = preferences.getString("input", null)?.let { Selection(it, preferences.getString("inputName", "").orEmpty()) },
        output = preferences.getString("output", null)?.let { Selection(it, preferences.getString("outputName", "").orEmpty()) },
        target = runCatching { Layout.valueOf(preferences.getString("target", "CBZ")!!) }.getOrDefault(Layout.CBZ),
        language = preferences.getString("language", "").orEmpty(),
        theme = runCatching { ThemeMode.valueOf(preferences.getString("theme", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM),
        enabledWarnings = NON_BLOCKING_CODES.filterTo(linkedSetOf()) {
            it.name !in preferences.getStringSet("disabledWarnings", emptySet()).orEmpty() && preferences.getBoolean("warnings", true)
        }
    ))
    val state = mutable.asStateFlow()
    private var control = Control()
    private var scanJob: Job? = null
    private var serviceJob: Job? = null
    var batchStarts: Int = 0
        private set

    fun select(input: Boolean, uri: Uri) {
        if (state.value.busy) return
        try {
            val node = SafStore(context).root(uri)
            val selection = Selection(uri.toString(), node.name)
            val key = if (input) "input" else "output"
            preferences.edit().putString(key, selection.uri).putString("${key}Name", selection.name).apply()
            mutable.update { (if (input) it.copy(input = selection) else it.copy(output = selection)).copy(phase = Phase.IDLE, plans = emptyList(), results = emptyMap(), error = null, progress = Progress()) }
        } catch (_: Exception) { mutable.update { it.copy(error = Issue(Code.PERMISSION_REQUIRED)) } }
    }
    fun target(layout: Layout) {
        if (state.value.busy || layout == Layout.INVALID || layout == state.value.target) return
        preferences.edit().putString("target", layout.name).apply()
        mutable.update { it.copy(target = layout, phase = Phase.IDLE, plans = emptyList(), results = emptyMap(), error = null, progress = Progress()) }
    }
    fun language(language: String) {
        preferences.edit().putString("language", language).apply()
        mutable.update { it.copy(language = language) }
    }
    fun theme(theme: ThemeMode) { preferences.edit().putString("theme", theme.name).apply(); mutable.update { it.copy(theme = theme) } }
    fun warnings(code: Code, show: Boolean) {
        if (code !in NON_BLOCKING_CODES) return
        val enabled = state.value.enabledWarnings.let { if (show) it + code else it - code }
        preferences.edit().remove("warnings")
            .putStringSet("disabledWarnings", NON_BLOCKING_CODES.filterNot { it in enabled }.mapTo(mutableSetOf()) { it.name }).apply()
        mutable.update { it.copy(enabledWarnings = enabled) }
    }
    fun error(issue: Issue) { mutable.update { it.copy(error = issue) } }
    fun scan() {
        val snapshot = state.value
        if (snapshot.busy || snapshot.input == null || snapshot.output == null) return
        control = Control()
        mutable.update { it.copy(phase = Phase.SCANNING, plans = emptyList(), results = emptyMap(), error = null, progress = Progress()) }
        scanJob = scope.launch {
            try {
                val store = SafStore(context)
                val input = store.root(Uri.parse(snapshot.input.uri))
                val output = store.root(Uri.parse(snapshot.output.uri))
                val plans = GalleryScanner(store).scan(input, output, snapshot.target, control) { count ->
                    mutable.update { it.copy(progress = Progress(completed = count)) }
                }
                mutable.update { it.copy(phase = Phase.READY, plans = plans, progress = Progress(total = plans.size)) }
            } catch (_: CancellationException) { mutable.update { it.copy(phase = Phase.CANCELLED) } }
            catch (e: BridgeFailure) { mutable.update { it.copy(phase = Phase.IDLE, error = e.issue) } }
            catch (_: Exception) { mutable.update { it.copy(phase = Phase.IDLE, error = Issue(Code.READ_WRITE_FAILURE)) } }
        }
    }
    fun begin() {
        if (state.value.phase != Phase.READY || state.value.plans.none { it.operation != Operation.SKIP }) return
        control = Control()
        mutable.update { it.copy(phase = Phase.RUNNING, progress = Progress(total = it.plans.size), results = emptyMap(), error = null) }
        try { ContextCompat.startForegroundService(context, Intent(context, BatchService::class.java)) }
        catch (_: Exception) { mutable.update { it.copy(phase = Phase.READY, error = Issue(Code.READ_WRITE_FAILURE)) } }
    }
    fun execute(onProgress: (Progress) -> Unit, onFinished: () -> Unit) {
        if (state.value.phase != Phase.RUNNING || serviceJob?.isActive == true) { onFinished(); return }
        val snapshot = state.value
        batchStarts++
        serviceJob = scope.launch {
            try {
                val store = SafStore(context)
                var lastUiUpdate = 0L
                var lastCompleted = -1
                var lastName = ""
                BatchConverter(store).run(store.root(Uri.parse(snapshot.input!!.uri)), store.root(Uri.parse(snapshot.output!!.uri)), snapshot.plans, control,
                    onProgress = { progress ->
                        val now = android.os.SystemClock.elapsedRealtime()
                        if (now - lastUiUpdate >= 100 || progress.completed != lastCompleted || progress.name != lastName) {
                            lastUiUpdate = now; lastCompleted = progress.completed; lastName = progress.name
                            mutable.update { it.copy(progress = progress) }
                        }
                        onProgress(progress)
                    },
                    onResult = { result -> mutable.update { it.copy(results = it.results + (result.sourceId to result)) } })
                mutable.update { it.copy(phase = if (control.isCancelled) Phase.CANCELLED else Phase.FINISHED) }
            } catch (e: BridgeFailure) { mutable.update { it.copy(phase = Phase.FINISHED, error = e.issue) } }
            catch (_: CancellationException) { mutable.update { it.copy(phase = Phase.CANCELLED) } }
            catch (_: Exception) { mutable.update { it.copy(phase = Phase.FINISHED, error = Issue(Code.READ_WRITE_FAILURE)) } }
            finally { onFinished() }
        }
    }
    fun cancel() { control.cancel() }
}
