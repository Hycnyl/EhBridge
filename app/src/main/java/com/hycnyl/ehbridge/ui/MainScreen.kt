package com.hycnyl.ehbridge.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hycnyl.ehbridge.*
import com.hycnyl.ehbridge.R
import com.hycnyl.ehbridge.model.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(state: UiState, repository: BridgeRepository, onPick: (Boolean) -> Unit, onStart: () -> Unit, onLanguage: (String) -> Unit, onExport: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Plan?>(null) }
    var cancelling by remember(state.phase) { mutableStateOf(false) }
    var filter by remember(state.plans) { mutableStateOf(GalleryFilter.ALL) }
    val filteredPlans = remember(state.plans, filter) { state.plans.filter(filter::matches) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(state.plans) { listState.scrollToItem(0) }
    val cancel = { cancelling = true; repository.cancel() }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                            Icon(Icons.Outlined.SwapHoriz, null, Modifier.padding(9.dp).size(25.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Column {
                            Text("EhBridge", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            if (LocalDensity.current.fontScale < 1.6f) Text(stringResource(R.string.app_subtitle), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.menu)) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.language)) }, leadingIcon = { Icon(Icons.Outlined.Language, null) }, onClick = { menu = false; dialog = "language" })
                            DropdownMenuItem(text = { Text(stringResource(R.string.theme)) }, leadingIcon = { Icon(Icons.Outlined.Palette, null) }, onClick = { menu = false; dialog = "theme" })
                            DropdownMenuItem(text = { Text(stringResource(R.string.show_warnings)) }, leadingIcon = { Icon(Icons.Outlined.NotificationsNone, null) }, onClick = { menu = false; dialog = "warnings" })
                            DropdownMenuItem(text = { Text(stringResource(R.string.export_report)) }, leadingIcon = { Icon(Icons.Outlined.SaveAlt, null) }, enabled = state.plans.isNotEmpty(), onClick = { menu = false; onExport() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.about)) }, leadingIcon = { Icon(Icons.Outlined.Info, null) }, onClick = { menu = false; dialog = "about" })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            val wideControlWidth = if (maxWidth >= 1200.dp) 380.dp else 330.dp
            if (maxWidth >= 840.dp) {
                Row(Modifier.fillMaxSize().testTag("wide_layout")) {
                    Column(Modifier.width(wideControlWidth).fillMaxHeight().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                        Controls(state, repository, onPick, onStart, cancel, cancelling)
                    }
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    FastScrollContainer(listState, Modifier.weight(1f).fillMaxHeight()) {
                        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            item { Overview(state, filter) { filter = it; scope.launch { listState.scrollToItem(0) } } }
                            if (state.plans.isEmpty()) item { EmptyState(state) }
                            else if (filteredPlans.isEmpty()) item { FilterEmptyState() }
                            itemsIndexed(filteredPlans, key = { index, plan -> "$index:${plan.source.id}" }) { _, plan -> GalleryRow(plan, state.results[plan.source.id], state.enabledWarnings) { selected = plan } }
                            item { Spacer(Modifier.height(12.dp)) }
                        }
                    }
                }
            } else {
                FastScrollContainer(listState, Modifier.fillMaxSize().testTag("compact_layout")) {
                    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        item { Controls(state, repository, onPick, onStart, cancel, cancelling) }
                        item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
                        item { Overview(state, filter) { filter = it; scope.launch { listState.scrollToItem(2) } } }
                        if (state.plans.isEmpty()) item { EmptyState(state) }
                        else if (filteredPlans.isEmpty()) item { FilterEmptyState() }
                        itemsIndexed(filteredPlans, key = { index, plan -> "$index:${plan.source.id}" }) { _, plan -> GalleryRow(plan, state.results[plan.source.id], state.enabledWarnings) { selected = plan } }
                        item { Spacer(Modifier.height(8.dp)) }
                    }
                }
            }
        }
    }
    when (dialog) {
        "language" -> ChoiceDialog(stringResource(R.string.language), listOf("" to stringResource(R.string.system_default), "zh-CN" to stringResource(R.string.chinese), "en" to stringResource(R.string.english)), state.language, onDismiss = { dialog = null }) { dialog = null; onLanguage(it) }
        "theme" -> ChoiceDialog(stringResource(R.string.theme), listOf(ThemeMode.SYSTEM to stringResource(R.string.system_default), ThemeMode.LIGHT to stringResource(R.string.light), ThemeMode.DARK to stringResource(R.string.dark)), state.theme, onDismiss = { dialog = null }) { repository.theme(it); dialog = null }
        "warnings" -> WarningDialog(state.enabledWarnings, repository::warnings) { dialog = null }
        "about" -> TextDialog(stringResource(R.string.about), onDismiss = { dialog = null }) { AboutContent() }
    }
    selected?.let { plan ->
        val result = state.results[plan.source.id]
        TextDialog(stringResource(R.string.details), onDismiss = { selected = null }) {
            Text(plan.source.name, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(layoutLabel(plan.layout)), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.planned_action, stringResource(operationLabel(plan.operation))), style = MaterialTheme.typography.bodyMedium)
            result?.let { Text(stringResource(R.string.result, stringResource(outcomeLabel(it.outcome))), style = MaterialTheme.typography.bodyMedium) }
            if (plan.pages.isNotEmpty()) Text(stringResource(R.string.pages_found, plan.pages.size, plan.pages.maxOf { it.number }), style = MaterialTheme.typography.bodyMedium)
            val issues = result?.issues ?: plan.issues
            if (issues.isNotEmpty()) {
                SectionLabel(stringResource(R.string.issues))
                issues.forEach { IssueText(it, true, showCode = true) }
            }
            val warnings = (result?.warnings ?: plan.warnings).distinct().filter { it.code in state.enabledWarnings }
            if (warnings.isNotEmpty()) {
                SectionLabel(stringResource(R.string.warnings))
                warnings.forEach { IssueText(it, false, showCode = true) }
            }
        }
    }
}

@Composable
private fun Controls(state: UiState, repository: BridgeRepository, onPick: (Boolean) -> Unit, onStart: () -> Unit, onCancel: () -> Unit, cancelling: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel(stringResource(R.string.step_folders))
            BoxWithConstraints {
                if (maxWidth >= 550.dp) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FolderCard(state.input, true, state.busy, Modifier.weight(1f), onPick)
                        FolderCard(state.output, false, state.busy, Modifier.weight(1f), onPick)
                    }
                } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FolderCard(state.input, true, state.busy, Modifier.fillMaxWidth(), onPick)
                    FolderCard(state.output, false, state.busy, Modifier.fillMaxWidth(), onPick)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel(stringResource(R.string.step_target))
            BoxWithConstraints(Modifier.selectableGroup()) {
                if (maxWidth >= 550.dp) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TargetCard(Layout.LOOSE, state, repository, Modifier.weight(1f))
                    TargetCard(Layout.CBZ, state, repository, Modifier.weight(1f))
                } else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TargetCard(Layout.LOOSE, state, repository, Modifier.fillMaxWidth())
                    TargetCard(Layout.CBZ, state, repository, Modifier.fillMaxWidth())
                }
            }
            Text(stringResource(R.string.workflow_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = repository::scan, enabled = !state.busy && state.input != null && state.output != null, modifier = Modifier.testTag("scan")) {
                Icon(Icons.Outlined.Search, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                Text(stringResource(if (state.plans.isEmpty()) R.string.scan else R.string.rescan))
            }
            if (state.busy) {
                FilledTonalButton(onClick = onCancel, enabled = !cancelling, modifier = Modifier.testTag("cancel")) {
                    Icon(Icons.Outlined.StopCircle, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.cancel))
                }
            } else Button(onClick = onStart, enabled = state.phase == Phase.READY && state.plans.any { it.operation != Operation.SKIP }, modifier = Modifier.testTag("start")) {
                Icon(Icons.Outlined.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.start))
            }
        }
        if (state.busy) ProgressCard(state, cancelling)
        state.error?.let { issue ->
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer) {
                Box(Modifier.padding(16.dp)) { IssueText(issue, true) }
            }
        }
        if (state.phase == Phase.CANCELLED) Text(stringResource(R.string.cancelled_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FolderCard(selection: Selection?, input: Boolean, busy: Boolean, modifier: Modifier, onPick: (Boolean) -> Unit) {
    Surface(modifier, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(if (input) Icons.Outlined.FolderOpen else Icons.AutoMirrored.Outlined.DriveFileMove, null, Modifier.size(21.dp), tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(if (input) R.string.input_folder else R.string.output_folder), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            }
            Text(selection?.name ?: stringResource(R.string.folder_unselected), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(stringResource(if (input) R.string.input_hint else R.string.output_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { onPick(input) }, enabled = !busy, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.align(Alignment.End).testTag(if (input) "pick_input" else "pick_output")) {
                Text(stringResource(if (selection == null) R.string.choose else R.string.change)); Spacer(Modifier.width(6.dp)); Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun TargetCard(layout: Layout, state: UiState, repository: BridgeRepository, modifier: Modifier) {
    val selected = state.target == layout
    Surface(onClick = { repository.target(layout) }, enabled = !state.busy, modifier = modifier.semantics { this.selected = selected; role = Role.RadioButton }.testTag(if (layout == Layout.LOOSE) "target_a" else "target_b"), shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stringResource(if (layout == Layout.LOOSE) R.string.target_a else R.string.target_b), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(if (layout == Layout.LOOSE) R.string.loose_description else R.string.cbz_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ProgressCard(state: UiState, cancelling: Boolean) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(if (cancelling) R.string.cancelling else if (state.phase == Phase.SCANNING) R.string.scanning else R.string.notification_title), style = MaterialTheme.typography.titleSmall)
            if (state.phase == Phase.SCANNING) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.scanned_count, state.progress.completed), style = MaterialTheme.typography.bodySmall)
            } else {
                LinearProgressIndicator(progress = { state.progress.completed.toFloat() / maxOf(1, state.progress.total) }, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.gallery_progress, state.progress.completed, state.progress.total), style = MaterialTheme.typography.bodySmall)
                Text(state.progress.name, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(if (state.progress.filesTotal > 0) stringResource(R.string.file_progress, state.progress.filesDone, state.progress.filesTotal) else stringResource(R.string.copy_progress, state.progress.filesDone), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Overview(state: UiState, filter: GalleryFilter, onFilter: (GalleryFilter) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.results), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
        if (state.plans.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GalleryFilter.entries.forEach { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = { onFilter(option) },
                        label = { Text(stringResource(option.label, state.plans.count(option::matches))) },
                        modifier = Modifier.testTag("filter_${option.name.lowercase()}")
                    )
                }
            }
            if (state.results.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Outcome.entries.forEach { outcome ->
                        Text("${stringResource(outcomeLabel(outcome))} ${state.results.values.count { it.outcome == outcome }}", style = MaterialTheme.typography.labelMedium, color = if (outcome == Outcome.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Text(stringResource(when (state.phase) { Phase.FINISHED -> R.string.finished; Phase.CANCELLED -> R.string.cancelled; Phase.RUNNING -> R.string.notification_title; else -> R.string.preview }), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyState(state: UiState) {
    if (state.phase == Phase.SCANNING) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
            Icon(Icons.Outlined.FolderCopy, null, Modifier.padding(22.dp).size(36.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        val emptyScan = state.phase == Phase.READY
        Text(stringResource(if (emptyScan) R.string.no_items else R.string.idle_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(if (emptyScan) R.string.no_items_description else R.string.idle_description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.widthIn(max = 440.dp))
        Text(stringResource(R.string.idle_privacy), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GalleryRow(plan: Plan, result: Result?, enabledWarnings: Set<Code>, onClick: () -> Unit) {
    val issues = result?.issues ?: plan.issues
    val warnings = (result?.warnings ?: plan.warnings).distinct().filter { it.code in enabledWarnings }
    val error = issues.isNotEmpty() || result?.outcome == Outcome.FAILED
    Surface(onClick = onClick, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(10.dp), color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Icon(when (plan.operation) { Operation.COPY -> Icons.Outlined.ContentCopy; Operation.PACK, Operation.UNPACK -> Icons.Outlined.SwapHoriz; Operation.SKIP -> Icons.Outlined.Block }, null, Modifier.padding(10.dp).size(22.dp), tint = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(plan.source.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(layoutLabel(plan.layout)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LabelPill(stringResource(operationLabel(plan.operation)), error = plan.operation == Operation.SKIP)
                LabelPill(result?.let { stringResource(outcomeLabel(it.outcome)) } ?: stringResource(R.string.pending), error = result?.outcome == Outcome.FAILED)
                if (warnings.isNotEmpty()) LabelPill("${stringResource(R.string.warnings)} ${warnings.size}")
            }
            issues.firstOrNull()?.let { IssueText(it, true, compact = true) }
            if (issues.isEmpty()) warnings.firstOrNull()?.let { IssueText(it, false, compact = true) }
        }
    }
}

@Composable
private fun LabelPill(label: String, error: Boolean = false) {
    Surface(shape = RoundedCornerShape(8.dp), color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
        Text(label, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun IssueText(issue: Issue, error: Boolean, showCode: Boolean = false, compact: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (showCode) Text(issue.code.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(issueLabel(issue.code)), style = MaterialTheme.typography.bodySmall, color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = if (compact) 3 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis)
        if (issue.file.isNotEmpty()) Text(issue.file, style = MaterialTheme.typography.bodySmall, maxLines = if (compact) 2 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SectionLabel(label: String) { Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() }) }

@Composable
private fun <T> ChoiceDialog(title: String, options: List<Pair<T, String>>, selected: T, onDismiss: () -> Unit, onSelect: (T) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        Column { options.forEach { (value, label) ->
            Row(Modifier.fillMaxWidth().clickable { onSelect(value) }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected == value, onClick = { onSelect(value) }); Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            }
        } }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } })
}

@Composable
private fun TextDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(16.dp).widthIn(max = 660.dp).fillMaxWidth().fillMaxHeight(.9f), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.close)) }
            }
        }
    }
}

private enum class GalleryFilter(val label: Int) {
    ALL(R.string.count_all), LOOSE(R.string.count_a), CBZ(R.string.count_b), INVALID(R.string.count_invalid);

    fun matches(plan: Plan): Boolean = when (this) {
        ALL -> true
        LOOSE -> plan.layout == Layout.LOOSE && plan.issues.isEmpty()
        CBZ -> plan.layout == Layout.CBZ && plan.issues.isEmpty()
        INVALID -> plan.layout == Layout.INVALID || plan.issues.isNotEmpty()
    }
}

@Composable
private fun FilterEmptyState() {
    Text(stringResource(R.string.no_filter_items), modifier = Modifier.fillMaxWidth().padding(24.dp),
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun WarningDialog(enabled: Set<Code>, onChange: (Code, Boolean) -> Unit, onDismiss: () -> Unit) {
    TextDialog(stringResource(R.string.show_warnings), onDismiss = onDismiss) {
        Text(stringResource(R.string.warning_settings_description), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        NON_BLOCKING_CODES.forEach { code ->
            Row(Modifier.fillMaxWidth().toggleable(value = code in enabled, role = Role.Switch, onValueChange = { onChange(code, it) })
                .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(warningTitle(code)), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = code in enabled, onCheckedChange = null)
            }
        }
    }
}

private fun warningTitle(code: Code): Int = when (code) {
    Code.MISSING_EHVIEWER -> R.string.warning_missing_ehviewer
    Code.MISSING_COMICINFO -> R.string.warning_missing_comicinfo
    Code.MISSING_COVER -> R.string.warning_missing_cover
    Code.METADATA_REPAIRED -> R.string.warning_metadata_repaired
    Code.TOKEN_UNAVAILABLE -> R.string.warning_token_unavailable
    Code.TOKEN_SOURCE_CONFLICT -> R.string.warning_token_conflict
    Code.COMICINFO_DIFFERENT -> R.string.warning_comicinfo_different
    Code.GALLERY_INCOMPLETE -> R.string.warning_incomplete
    Code.SOURCE_STATE_NOT_TRANSFERRED -> R.string.warning_state_not_transferred
    Code.STAGING_CLEANUP_FAILED -> R.string.warning_cleanup_failed
    Code.XML_METADATA_LIMITED -> R.string.warning_xml_limited
    else -> issueLabel(code)
}
