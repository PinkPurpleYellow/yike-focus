package app.yike.focus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.yike.focus.domain.*
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppearanceEditor(settings: FocusSettings, onApply: (FocusSettings) -> Unit, onDismiss: () -> Unit) {
    var preset by rememberSaveable { mutableStateOf(settings.uiPreset.name) }
    var face by rememberSaveable { mutableStateOf(settings.timerFace.name) }
    var density by rememberSaveable { mutableStateOf(settings.uiDensity.name) }
    var motion by rememberSaveable { mutableStateOf(settings.decorativeMotion) }
    var color by rememberSaveable { mutableStateOf(settings.themeStyle.name) }
    var mode by rememberSaveable { mutableStateOf(settings.themeMode.name) }
    var material by rememberSaveable { mutableStateOf(settings.interfaceMaterial.name) }
    var transparency by rememberSaveable { mutableFloatStateOf(settings.glassTransparency) }
    var blur by rememberSaveable { mutableFloatStateOf(settings.glassBlur) }
    var readable by rememberSaveable { mutableStateOf(settings.glassReadable) }
    var page by rememberSaveable { mutableIntStateOf(0) }
    val draft = settings.copy(uiPreset = UiPreset.valueOf(preset), timerFace = TimerFace.valueOf(face),
        uiDensity = UiDensity.valueOf(density), decorativeMotion = motion,
        themeStyle = ThemeStyle.valueOf(color), themeMode = ThemeMode.valueOf(mode),
        interfaceMaterial = InterfaceMaterial.valueOf(material), glassTransparency = transparency,
        glassBlur = blur, glassReadable = readable)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        FocusTheme(draft) {
        FocusBackdropHost(draft, independent = true) {
        Surface(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).testTag("theme_editor"), color = androidx.compose.ui.graphics.Color.Transparent) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("外观与主题", style = MaterialTheme.typography.headlineMedium)
                        Text("先看看，再选一套适合自己的界面", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    FocusTextButton(onClick = onDismiss, modifier = Modifier.testTag("cancel_appearance")) { Text("取消") }
                }
                Spacer(Modifier.height(12.dp))
                val options: @Composable () -> Unit = {
                    Text("界面材质", style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        InterfaceMaterial.entries.forEach { item ->
                            FocusFilterChip(draft.interfaceMaterial == item, { material = item.name }, label = { Text(item.title) },
                                modifier = Modifier.heightIn(min = 48.dp).testTag("material_${item.name}"))
                        }
                    }
                    if (draft.interfaceMaterial == InterfaceMaterial.GLASS) {
                        Text("玻璃透明度 · ${(transparency * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                        FocusSlider(transparency, { transparency = it }, modifier = Modifier.testTag("glass_transparency"))
                        Text("模糊强度 · ${(blur * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                        FocusSlider(blur, { blur = it }, modifier = Modifier.testTag("glass_blur"))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text("增强可读性"); Text("增加底层厚度，减弱折射", style = MaterialTheme.typography.bodySmall, color = Muted) }
                            FocusSwitch(readable, { readable = it }, modifier = Modifier.testTag("glass_readable"))
                        }
                        FocusTextButton({ transparency = .5f; blur = .6f; readable = false }) { Text("恢复玻璃默认") }
                        Text("全页玻璃与壁纸相融；拖动导航、开关和滑块可感受透镜。旧安卓提供兼容效果。", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(24.dp))
                    Text("界面预设", style = MaterialTheme.typography.titleMedium)
                    UiPreset.entries.forEach { item ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).then(if(draft.interfaceMaterial == InterfaceMaterial.GLASS) Modifier.glassSurface(ButtonShape,GlassRole.CONTROL).background(Tomato.copy(alpha=if(draft.uiPreset==item) .12f else 0f),ButtonShape) else Modifier.background(if(draft.uiPreset==item) Peach else CardPaper,ButtonShape))
                            .then(if (draft.interfaceMaterial == InterfaceMaterial.TRADITIONAL) Modifier.border(1.dp, if (draft.uiPreset == item) Tomato else Line, ButtonShape) else Modifier)
                            .testTag("appearance_preset_${item.name}").selectable(draft.uiPreset == item, role = Role.RadioButton) { preset = item.name }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = draft.uiPreset == item, onClick = null, modifier = Modifier.padding(end = 12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.title, style = MaterialTheme.typography.titleMedium)
                                Text(item.description, color = Muted, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(when (item) { UiPreset.CLASSIC -> "◷"; UiPreset.MINIMAL -> "25:00"; UiPreset.FLIP -> "25│00"; UiPreset.JOURNAL -> "日记" },
                                color = Tomato, fontSize = 15.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Text("配色", style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeStyle.entries.forEach { item -> FocusFilterChip(draft.themeStyle == item, { color = item.name }, label = { Text(item.title, style = MaterialTheme.typography.bodyMedium) }, shape = SmallShape, modifier = Modifier.heightIn(min = 48.dp)) }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { item -> FocusFilterChip(draft.themeMode == item, { mode = item.name }, label = { Text(item.title, style = MaterialTheme.typography.bodyMedium) }, shape = SmallShape, modifier = Modifier.heightIn(min = 48.dp)) }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("计时器造型", style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TimerFace.entries.forEach { item -> FocusFilterChip(draft.timerFace == item, { face = item.name }, label = { Text(item.title, style = MaterialTheme.typography.bodyMedium) }, shape = SmallShape, modifier = Modifier.heightIn(min = 48.dp)) }
                    }
                    Text("造型覆盖应用于所有预设；选“跟随预设”使用各自的默认造型。", color = Muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(16.dp))
                    Text("内容密度", style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        UiDensity.entries.forEach { item -> FocusFilterChip(draft.uiDensity == item, { density = item.name }, label = { Text(item.title, style = MaterialTheme.typography.bodyMedium) }, shape = SmallShape, modifier = Modifier.heightIn(min = 48.dp)) }
                    }
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("装饰动效"); Text("遵循系统动画设置，暂停时不翻页", color = Muted, style = MaterialTheme.typography.bodySmall) }
                        FocusSwitch(motion, { motion = it })
                    }
                    FocusTextButton(onClick = {
                        val reset = draft.resetAppearanceDefaults()
                        face = reset.timerFace.name; density = reset.uiDensity.name; motion = reset.decorativeMotion
                    }) { Text("恢复预设默认") }
                    Text("只重置造型、密度及动效，保留配色、明暗与背景图。", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                val preview: @Composable () -> Unit = {
                    PreviewPages(page) { page = it }
                    Spacer(Modifier.height(8.dp))
                    Text("示例数据预览 · 不影响计时和记录", color = Muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    AppearancePreview(draft, page) { page = it }
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    if (maxWidth >= 1050.dp) Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Column(Modifier.width(360.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(end = 8.dp)) { options() }
                        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) { preview() }
                    } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        options(); preview()
                    }
                }
                HorizontalDivider(color = Line)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    FocusButton(onClick = { onApply(draft) }, modifier = Modifier.heightIn(min = 48.dp).testTag("apply_appearance")) { Text("应用这套外观") }
                }
            }
        }
        }
        }
    }
}

@Composable
internal fun PreviewPages(page: Int, onPage: (Int) -> Unit) {
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.widthIn(max = 440.dp).fillMaxWidth()) {
        val width = maxOf((maxWidth - 8.dp) / 4, (58 * density.fontScale).dp)
        LaunchedEffect(page, width, scroll.maxValue) {
            val target = with(density) { (width * page - (maxWidth - width) / 2).roundToPx() }
            scroll.scrollTo(target.coerceIn(0, scroll.maxValue))
        }
        val glass = LocalAppearance.current.interfaceMaterial == InterfaceMaterial.GLASS
        Row(Modifier.clip(ButtonShape).then(if (glass) Modifier.glassSurface(ButtonShape, GlassRole.CONTROL)
            else Modifier.background(CardPaper, ButtonShape)).horizontalScroll(scroll).selectableGroup().padding(4.dp)) {
            listOf("专注", "统计", "设置", "全屏").forEachIndexed { index, title ->
                val selected = page == index
                val interaction = remember { MutableInteractionSource() }
                Box(Modifier.width(width).heightIn(min = 48.dp).testTag("preview_page_$index")
                    .background(if (selected) Peach else Color.Transparent, SmallShape)
                    .focusOutline(interaction, SmallShape).selectable(selected, interactionSource = interaction,
                        indication = null, role = Role.Tab, onClick = { onPage(index) }), contentAlignment = Alignment.Center) {
                    Text(title, color = if (selected) Tomato else Ink, maxLines = 1,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
private fun AppearancePreview(settings: FocusSettings, page: Int, onPage: (Int) -> Unit) {
    val today = remember { LocalDate.now() }
    val records = remember { (0L..6L).map { i ->
        val start = today.minusDays(i).atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = start + (15 + i * 5) * 60_000
        FocusRecord("preview-$i", null, "阅读与学习", start, end, end - start, true, listOf(FocusSpan(start, end)))
    } }
    val stats = Statistics.forDay(records, today)
    val state = TimerEngine.ready(settings)
    val tasks = remember { listOf(app.yike.focus.data.TaskEntity(id = "preview-task", title = "阅读 20 页，整理学习笔记")) }
    FocusTheme(settings) {
        val shareWallpaper = settings.interfaceMaterial == InterfaceMaterial.GLASS && settings.backgroundFile.isNotEmpty()
        Box(Modifier.fillMaxWidth().height(560.dp).clip(ButtonShape)
            .then(if (shareWallpaper) Modifier else Modifier.background(Paper)).border(1.dp, Line, ButtonShape)) {
            FocusBackdropHost(settings, enabled = settings.interfaceMaterial == InterfaceMaterial.GLASS || page == 0 || page == 3,
                independent = !shareWallpaper) {
            if (page == 3) FullscreenTimer(state, settings, 0, tasks.first().title, {}, {}, {}, { onPage(0) })
            else AppScaffold(settings, page, today, onPage = onPage) {
                when (page) {
                    0 -> FocusScreen(state, settings, 0, tasks, tasks.first().id, stats,
                        onStart = {}, onPause = {}, onEnd = {}, onSettings = { onPage(2) },
                        onSelectTask = {}, onSaveTask = {}, onDeleteTask = {}, onAmbient = {}, onVolume = {},
                        onFullscreen = { onPage(3) }, onPreview = {}, previewing = false)
                    1 -> StatsScreen(records, null, today, stats, 8)
                    else -> {
                        SectionTitle("找到适合你的节奏。", "时间、声音和提醒，都可以按你的习惯来。")
                        Spacer(Modifier.height(LayoutGap))
                        SettingsPanels(listOf("专注节奏", "声音与屏幕", "外观与主题", "备份与恢复", "后台与提醒", "关于").map { title ->
                            SettingsSection(title) { PaperCard(Modifier.fillMaxWidth()) {
                                Text(title, style = MaterialTheme.typography.titleLarge)
                                Spacer(Modifier.height(16.dp))
                                Text(when (title) {
                                    "专注节奏" -> "专注 25 分钟 · 短休息 5 分钟\n长休息 15 分钟 · 每日目标 8 轮"
                                    "外观与主题" -> "${settings.uiPreset.title} · ${settings.themeStyle.title}\n${settings.themeMode.title} · ${settings.uiDensity.title}"
                                    "关于" -> "一刻专注 ${app.yike.focus.BuildConfig.VERSION_NAME}\nPinkPurpleYellow"
                                    "备份与恢复" -> "导出备份 · 导入合并"
                                    "后台与提醒" -> "按需开启计时提醒"
                                    else -> "轻柔提示音 · 专注时屏幕常亮"
                                }, color = Muted, style = MaterialTheme.typography.bodyMedium)
                            } }
                        })
                    }
                }
            }
            }
        }
    }
}
