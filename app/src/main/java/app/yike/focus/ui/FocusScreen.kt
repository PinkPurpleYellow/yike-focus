package app.yike.focus.ui

import kotlinx.coroutines.launch

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yike.focus.data.TaskEntity
import app.yike.focus.domain.*
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun FocusScreen(
    state: TimerState, settings: FocusSettings, elapsed: Long,
    tasks: List<TaskEntity>, selectedTaskId: String?, today: DayStats,
    onStart: () -> Unit, onPause: () -> Unit, onEnd: () -> Unit, onSettings: () -> Unit,
    onSelectTask: (String?) -> Unit, onSaveTask: (TaskEntity) -> Unit, onDeleteTask: (TaskEntity) -> Unit,
    onAmbient: (Ambient) -> Unit, onVolume: (Float) -> Unit,
    onFullscreen: () -> Unit, onPreview: () -> Unit, previewing: Boolean,
) {
    var confirmEnd by remember { mutableStateOf(false) }
    val selectedTask = tasks.find { it.id == selectedTaskId && !it.completed }
    SectionTitle(if (state.phase == Phase.FOCUS) "把注意力，留给这一刻。" else "休息，也是进步的一部分。",
        if (state.phase == Phase.FOCUS) "不必一下完成所有事，从一个番茄开始。" else "起身、远眺，给自己一点呼吸的时间。")
    Spacer(Modifier.height(LayoutGap))
    val timer: @Composable (Modifier)->Unit = { modifier ->
        TimerCard(state, settings, elapsed, selectedTask?.title, onStart, onPause,
            { if (state.phase == Phase.FOCUS) confirmEnd = true else onEnd() }, onSettings,
            onAmbient, onVolume, onFullscreen, onPreview, previewing, modifier)
    }
    val taskList: @Composable (Modifier)->Unit = { m -> TasksCard(tasks, selectedTaskId, state, onSelectTask, onSaveTask, onDeleteTask, m) }
    val summary: @Composable ()->Unit = { SummaryCards(today, settings.dailyGoal) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val repo = LocalDeskRepo.current
    WidgetDesk("focus", fallback = { FocusPageLayout(summary, timer, taskList) }, emergency = {
        if (state.status != RunStatus.READY) FocusButton(onFullscreen) { Text("返回计时控制") }
    }) { t -> when(t.id) {
        "timer" -> if(t.h==1 || t.w==1) PaperCard(Modifier.fillMaxWidth()) {
            Text(state.phase.title); Text(durationText(state.remaining(elapsed)), style=MaterialTheme.typography.headlineMedium)
            FocusButton(if(state.status==RunStatus.RUNNING) onPause else onStart){Text(if(state.status==RunStatus.RUNNING) "暂停" else "开始 / 继续")}; FocusTextButton(onFullscreen){Text("全屏控制")}
        } else timer(Modifier.fillMaxWidth())
        "tasks" -> taskList(Modifier.fillMaxWidth())
        "summary" -> summary()
        "next" -> PaperCard(Modifier.fillMaxWidth()) {
            val task=selectedTask ?: tasks.firstOrNull { !it.completed };Text("下一件任务", style=MaterialTheme.typography.titleMedium);Text(task?.title ?: "自由专注")
            FocusButton({scope.launch { repo?.selectTask(task?.id);onStart() }}, enabled=state.status==RunStatus.READY){Text("开始这一轮")}
        }
        "audio" -> PaperCard(Modifier.fillMaxWidth()) {
            Text("背景声", style=MaterialTheme.typography.titleMedium);var open by remember { mutableStateOf(false) }
            Box {FocusOutlinedButton({open=true}){Text(settings.ambient.title)};FocusDropdownMenu(open,{open=false}){ Ambient.entries.forEach{a->FocusMenuItem({Text(a.title)},{onAmbient(a);open=false})} }}
            if(settings.ambient!=Ambient.OFF){FocusTextButton(onPreview){Text(if(previewing) "停止试听" else "试听")};var volume by remember(settings.ambientVolume){mutableFloatStateOf(settings.ambientVolume)};FocusSlider(volume,{volume=it},onValueChangeFinished={onVolume(volume)})}
        }
        "garden" -> GardenWidget()
    } }
    if (confirmEnd) FocusAlertDialog(
        onDismissRequest = { confirmEnd = false }, title = { Text("结束这次专注？") },
        text = { Text("已专注的时间会保留在统计中，本次不计为完整一轮。") },
        confirmButton = { FocusTextButton(onClick = { confirmEnd = false; onEnd() }) { Text("结束并保存") } },
        dismissButton = { FocusTextButton(onClick = { confirmEnd = false }) { Text("继续专注") } },
    )
}

@Composable
fun SummaryCards(stats: DayStats, goal: Int) {
    WallpaperContent(enabled = false) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stacked = maxWidth < 560.dp || LocalDensity.current.fontScale >= 1.6f
        val items = listOf(Triple("今日专注", "${stats.focusMs / 60_000}", "分钟"),
            Triple("完成番茄", "${stats.completedRounds}", "轮"),
            Triple("每日目标", "${stats.completedRounds} / $goal", "轮"))
        if (stacked) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { (label, value, unit) -> SummaryCard(label, value, unit, Modifier.fillMaxWidth(), true) }
        } else Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items.forEach { (label, value, unit) -> SummaryCard(label, value, unit, Modifier.weight(1f), false) }
        }
    }
}

}

@Composable
private fun SummaryCard(label: String, value: String, unit: String, modifier: Modifier, stacked: Boolean) {
    val background = CardPaper.copy(alpha = if (LocalAppearance.current.uiPreset == UiPreset.MINIMAL) 0.94f else 1f)
    val surface = if (LocalAppearance.current.interfaceMaterial == InterfaceMaterial.GLASS) Modifier.glassSurface(PanelShape) else Modifier.background(background, PanelShape)
    val padding = if (LocalAppearance.current.uiDensity == UiDensity.COMPACT) 12.dp else 16.dp
    val number: @Composable RowScope.() -> Unit = {
        Text(value, Modifier.alignByBaseline(), style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.width(4.dp))
        Text(unit, Modifier.alignByBaseline(), color = Muted, style = MaterialTheme.typography.bodySmall)
    }
    if (stacked) Row(modifier.then(surface).padding(padding), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = Muted, style = MaterialTheme.typography.bodyMedium)
        Row(content = number)
    } else Column(modifier.then(surface).padding(padding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = Muted, style = MaterialTheme.typography.bodyMedium)
        Row(content = number)
    }
}

@Composable
fun FocusPageLayout(summary: @Composable () -> Unit, timer: @Composable (Modifier) -> Unit,
    tasks: @Composable (Modifier) -> Unit) {
    val preset = LocalAppearance.current.uiPreset
    val gap = LayoutGap
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val dual = maxWidth >= 840.dp && LocalDensity.current.fontScale < 1.6f
        Column(Modifier.widthIn(max = if (preset == UiPreset.MINIMAL) 800.dp else 1280.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(gap)) {
            when {
                preset == UiPreset.MINIMAL -> { timer(Modifier.fillMaxWidth()); summary(); tasks(Modifier.fillMaxWidth()) }
                preset == UiPreset.JOURNAL && dual -> Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    Column(Modifier.weight(1.5f), verticalArrangement = Arrangement.spacedBy(gap)) { timer(Modifier.fillMaxWidth()); summary() }
                    tasks(Modifier.weight(1f))
                }
                dual -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.Top) {
                        if (preset == UiPreset.FLIP) tasks(Modifier.weight(1f))
                        timer(Modifier.weight(1.5f))
                        if (preset != UiPreset.FLIP) tasks(Modifier.weight(1f))
                    }
                    summary()
                }
                else -> { timer(Modifier.fillMaxWidth()); summary(); tasks(Modifier.fillMaxWidth()) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimerCard(
    state: TimerState, settings: FocusSettings, elapsed: Long, selectedTaskTitle: String?,
    onStart: () -> Unit, onPause: () -> Unit, onEnd: () -> Unit, onSettings: () -> Unit,
    onAmbient: (Ambient) -> Unit, onVolume: (Float) -> Unit, onFullscreen: () -> Unit,
    onPreview: () -> Unit, previewing: Boolean, modifier: Modifier,
) {
    val isFocus = state.phase == Phase.FOCUS
    var soundsExpanded by rememberSaveable { mutableStateOf(false) }
    var showTask by remember { mutableStateOf(false) }
    val title = if (!isFocus) "下一轮，由你开启" else if (state.status == RunStatus.READY) selectedTaskTitle ?: "自由专注" else state.taskTitle
    val clockHeight = (LocalContentHeight.current - 360.dp).coerceIn(120.dp,
        if (settings.uiDensity == UiDensity.COMPACT) 220.dp else 260.dp)
    PaperCard(modifier.testTag("timer_card")) {
        val accent = if (isFocus) Tomato else Sage
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Column(Modifier.padding(vertical = 4.dp)) {
                Text(state.phase.title + when (state.status) { RunStatus.PAUSED -> " · 已暂停"; RunStatus.READY -> " · 准备开始"; else -> " · 进行中" },
                    color = accent, style = MaterialTheme.typography.titleSmall)
                Text("第 ${if (isFocus) (state.cycleCompleted + 1).coerceAtMost(settings.longBreakEvery) else state.cycleCompleted} / ${settings.longBreakEvery} 轮",
                    color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            FocusTextButton(onClick = onFullscreen, modifier = Modifier.testTag("enter_fullscreen")) {
                Glyph("fullscreen", Modifier.size(20.dp), accent)
                Spacer(Modifier.width(8.dp)); Text("全屏", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(8.dp))
        TimerDisplay(state.remaining(elapsed), state.durationMs, state.phase, state.status, settings, heightLimit = clockHeight)
        Spacer(Modifier.height(12.dp))
        Text(title, Modifier.fillMaxWidth().clickable { showTask = true }.heightIn(min = 48.dp).wrapContentHeight(Alignment.CenterVertically).testTag("current_task"),
            color = Ink, style = MaterialTheme.typography.titleMedium, maxLines = 2,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FocusButton(onClick = if (state.status == RunStatus.RUNNING) onPause else onStart,
                modifier = Modifier.widthIn(min = 172.dp).testTag("timer_primary"),
                colors = ButtonDefaults.buttonColors(containerColor = accent)) {
                Glyph(if (state.status == RunStatus.RUNNING) "pause" else "play", Modifier.size(20.dp), if (settings.interfaceMaterial == InterfaceMaterial.GLASS) Ink else MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(8.dp))
                Text(when (state.status) { RunStatus.READY -> "开始专注"; RunStatus.RUNNING -> "暂停"; RunStatus.PAUSED -> "继续" })
            }
            if (state.status != RunStatus.READY) FocusOutlinedButton(onClick = onEnd) { Text(if (isFocus) "结束" else "跳过休息") }
            else FocusTextButton(onClick = onSettings) { Text("调整时长", color = Muted) }
        }
        Spacer(Modifier.height(16.dp))
        Text(if (isFocus) "每 ${settings.longBreakEvery} 轮长休息一次" else "休息结束后，手动开启下一轮",
            Modifier.fillMaxWidth(), color = Muted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp)); HorizontalDivider(color = Line)
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            FocusTextButton(onClick = { soundsExpanded = !soundsExpanded }, modifier = Modifier.testTag("expand_sounds"),
                contentPadding = PaddingValues(vertical = 12.dp)) {
                Text("背景声 · ${if (settings.ambient == Ambient.OFF) "安静" else settings.ambient.title}",
                    style = MaterialTheme.typography.bodyMedium, color = Muted)
                Spacer(Modifier.width(8.dp)); Glyph(if (soundsExpanded) "up" else "down", Modifier.size(16.dp), Muted)
            }
            if (settings.ambient != Ambient.OFF) FocusTextButton(onClick = onPreview) {
                Text(if (previewing) "停止试听" else "试听 12 秒", style = MaterialTheme.typography.bodyMedium)
            }
        }
        androidx.compose.animation.AnimatedVisibility(soundsExpanded,
            enter = androidx.compose.animation.expandVertically(androidx.compose.animation.core.tween(motionMillis(280))) +
                androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(motionMillis(200))),
            exit = androidx.compose.animation.shrinkVertically(androidx.compose.animation.core.tween(motionMillis(280))) +
                androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(motionMillis(200)))) {
            Column {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Ambient.entries.forEach { ambient ->
                        FocusFilterChip(selected = settings.ambient == ambient, onClick = { onAmbient(ambient) },
                            modifier = Modifier.heightIn(min = 48.dp), shape = SmallShape,
                            label = { Text(if (ambient == Ambient.OFF) "安静" else ambient.title, style = MaterialTheme.typography.bodyMedium) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = SageLight, selectedLabelColor = Sage))
                    }
                }
                if (settings.ambient != Ambient.OFF) {
                    var draft by remember(settings.ambientVolume) { mutableFloatStateOf(settings.ambientVolume) }
                    Text("音量 · ${(draft * 100).toInt()}%", color = Muted, style = MaterialTheme.typography.bodySmall)
                    FocusSlider(value = draft, onValueChange = { draft = it }, onValueChangeFinished = { onVolume(draft) })
                }
                Text("背景声仅在专注中播放；暂停和休息时停止。", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (showTask) FocusAlertDialog(onDismissRequest = { showTask = false }, title = { Text("当前任务") },
        text = { Text(title, Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { FocusTextButton(onClick = { showTask = false }) { Text("关闭") } })
}

@Composable
private fun TasksCard(
    tasks: List<TaskEntity>, selectedTaskId: String?, state: TimerState,
    onSelect: (String?) -> Unit, onSave: (TaskEntity) -> Unit, onDelete: (TaskEntity) -> Unit,
    modifier: Modifier,
) {
    var editing by remember { mutableStateOf<TaskEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<TaskEntity?>(null) }
    var showCompleted by rememberSaveable { mutableStateOf(false) }
    val pending = tasks.filterNot { it.completed }
    val completed = tasks.filter { it.completed }
    val visible = if (showCompleted) tasks else pending
    PaperCard(modifier.testTag("tasks_card")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("今天想完成", style = MaterialTheme.typography.titleLarge)
                Text("一件一件，慢慢来", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            FocusIconButton(onClick = { adding = true }, modifier = Modifier.testTag("add_task").semantics { contentDescription = "添加任务" },tonal=true) {
                Glyph("add", color = Tomato)
            }
        }
        Spacer(Modifier.height(20.dp))
        Surface(modifier=Modifier.glassSurface(if(settingsClassic()) PanelShape else SmallShape,GlassRole.CONTROL),onClick = { onSelect(null) }, shape = if (settingsClassic()) PanelShape else SmallShape, color = if(LocalAppearance.current.interfaceMaterial == InterfaceMaterial.GLASS) Sage.copy(alpha=if(selectedTaskId==null) .12f else 0f) else (if(selectedTaskId==null) SageLight else Paper)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Glyph("leaf", Modifier.size(20.dp), Sage); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text("自由专注", style = MaterialTheme.typography.titleMedium); Text("不关联任务，也很好", style = MaterialTheme.typography.bodySmall, color = Muted) }
                if (selectedTaskId == null) Glyph("check", Modifier.size(16.dp), Sage)
            }
        }
        Spacer(Modifier.height(16.dp))
        if (visible.isEmpty()) Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Glyph("leaf", Modifier.size(36.dp), Sage.copy(alpha = 0.7f)); Spacer(Modifier.height(12.dp))
            Text(if (tasks.isEmpty()) "写下今天的一件小事" else "待办都完成了，做得不错", color = Muted, style = MaterialTheme.typography.bodyMedium)
            FocusTextButton(onClick = { adding = true }) { Text("添加任务") }
        } else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 490.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(visible, key = { it.id }) { task ->
                var menu by remember { mutableStateOf(false) }
                val selected = task.id == selectedTaskId && !task.completed
                Row(Modifier.fillMaxWidth().background(if(selected) (if(LocalAppearance.current.interfaceMaterial==InterfaceMaterial.GLASS) Tomato.copy(alpha=.12f) else Peach.copy(alpha=.65f)) else Color.Transparent, if (settingsClassic()) PanelShape else SmallShape)
                    .clip(if (settingsClassic()) PanelShape else SmallShape).clickable(enabled = !task.completed) { onSelect(task.id) }.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FocusCheckbox(modifier = Modifier.semantics { contentDescription = "${task.title}的完成状态" }, checked = task.completed, onCheckedChange = { onSave(task.copy(completed = it)) })
                    Column(Modifier.weight(1f)) {
                        Text(task.title, style = MaterialTheme.typography.bodyLarge, color = if (task.completed) Muted else Ink,
                            maxLines = 3, overflow = TextOverflow.Ellipsis,
                            textDecoration = if (task.completed) androidx.compose.ui.text.style.TextDecoration.LineThrough else null)
                        if (selected) Text("已选为专注任务", style = MaterialTheme.typography.bodySmall, color = Tomato)
                    }
                    Box {
                        FocusIconButton(onClick = { menu = true }, modifier = Modifier.semantics { contentDescription = "${task.title}的更多操作" }) { Glyph("more", Modifier.size(16.dp), Muted) }
                        FocusDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            FocusMenuItem(text = { Text("查看与编辑") }, onClick = { menu = false; editing = task })
                            FocusMenuItem(text = { Text("删除") }, onClick = { menu = false; deleting = task })
                        }
                    }
                }
            }
        }
        if (completed.isNotEmpty()) FocusTextButton(onClick = { showCompleted = !showCompleted }) { Text("${if (showCompleted) "收起" else "查看"}已完成 · ${completed.size}", color = Muted) }
        if (state.status != RunStatus.READY) {
            Spacer(Modifier.height(12.dp))
            Text("更换选择会用于下一轮，当前记录保持原任务。", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(16.dp)); HorizontalDivider(color = Line); Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.Top) {
            Glyph("leaf", Modifier.size(16.dp), Sage); Spacer(Modifier.width(12.dp))
            Text("专注不需要完美。\n愿意开始，就已经很好。", color = Muted, style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (adding || editing != null) TaskDialog(editing,
        onDismiss = { adding = false; editing = null },
        onSave = { title -> onSave(editing?.copy(title = title) ?: TaskEntity(title = title)); adding = false; editing = null })
    deleting?.let { task -> FocusAlertDialog(onDismissRequest = { deleting = null }, title = { Text("删除这项任务？") },
        text = { Text("“${task.title}”将从清单中移除，历史专注记录会保留。") },
        confirmButton = { FocusTextButton(onClick = { onDelete(task); deleting = null }) { Text("删除") } },
        dismissButton = { FocusTextButton(onClick = { deleting = null }) { Text("取消") } }) }
}

@Composable
private fun TaskDialog(task: TaskEntity?, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var title by remember(task?.id) { mutableStateOf(task?.title ?: "") }
    FocusAlertDialog(onDismissRequest = onDismiss, title = { Text(if (task == null) "添加一件小事" else "编辑任务") },
        text = { FocusTextField(value = title, onValueChange = { title = it.take(120) }, label = { Text("任务名称") },
            placeholder = { Text("比如：读完书的第三章") }, modifier = Modifier.fillMaxWidth().testTag("task_title"), minLines = 2, maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (title.isNotBlank()) onSave(title.trim()) }),
            supportingText = { Text("${title.length}/120") }) },
        confirmButton = { FocusTextButton(onClick = { onSave(title.trim()) }, enabled = title.isNotBlank(), modifier = Modifier.testTag("save_task")) { Text("保存") } },
        dismissButton = { FocusTextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
private fun settingsClassic() = LocalAppearance.current.uiPreset == UiPreset.CLASSIC
