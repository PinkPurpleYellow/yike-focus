package app.yike.focus.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.yike.focus.domain.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StatsScreen(records: List<FocusRecord>, liveRecord: FocusRecord?, today: LocalDate, todayStats: DayStats, dailyGoal: Int) {
    var selectedDateText by rememberSaveable { mutableStateOf(today.toString()) }
    var heatDetail by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedDate = LocalDate.parse(selectedDateText)
    var showDatePicker by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<FocusRecord?>(null) }
    val zone = ZoneId.systemDefault()
    val savedWeek = remember(records, today, zone) { (6 downTo 0).map { Statistics.forDay(records, today.minusDays(it.toLong()), zone) } }
    val week = if (liveRecord == null) savedWeek else savedWeek.map { day ->
        day.copy(focusMs = day.focusMs + Statistics.forDay(listOf(liveRecord), day.date, zone).focusMs)
    }
    val selectedStats = remember(records, selectedDate, zone) { Statistics.forDay(records, selectedDate, zone) }
    val history = remember(records, selectedDate, zone) { records.filter { Statistics.forDay(listOf(it), selectedDate, zone).focusMs > 0 } }

    val preset = LocalAppearance.current.uiPreset
    val gap = LayoutGap
    SectionTitle("每一刻，都算数。", "看见自己的积累，也给不完美的一天留些余地。")
    Spacer(Modifier.height(gap))
    val trend: @Composable () -> Unit = {
    PaperCard(Modifier.fillMaxWidth()) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("最近七天", style = MaterialTheme.typography.titleLarge)
            Text("累计 ${week.sumOf { it.focusMs } / 60_000} 分钟", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(24.dp))
        WeeklyChart(week, today, selectedDate) { selectedDateText = it.toString() }
        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = Line)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Glyph("leaf", Modifier.size(20.dp), Sage); Spacer(Modifier.width(8.dp))
            Text(if (todayStats.completedRounds >= dailyGoal) "今天的目标已达成。记得也给自己一点休息。" else "今日目标 ${todayStats.completedRounds} / $dailyGoal 轮，按照自己的节奏来。",
                color = Muted, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(progress = { (todayStats.completedRounds.toFloat() / dailyGoal).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp), color = Sage, trackColor = SageLight)
    }
    }
    val entries: @Composable () -> Unit = {
    PaperCard(Modifier.fillMaxWidth()) {
        Text("专注记录", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Text(selectedDate.format(DateTimeFormatter.ofPattern("yyyy年M月d日")), style = MaterialTheme.typography.titleMedium)
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FocusTextButton(onClick = { selectedDateText = selectedDate.minusDays(1).toString() }) { Text("前一天") }
            FocusTextButton(onClick = { selectedDateText = selectedDate.plusDays(1).toString() }, enabled = selectedDate < today) { Text("后一天") }
            FocusTextButton(onClick = { showDatePicker = true }) { Text("选日期") }
            if (selectedDate != today) FocusTextButton({ selectedDateText = today.toString() }) { Text("回到今天") }
        }
        Text("${selectedStats.completedRounds} 轮完成 · ${durationText(selectedStats.focusMs)}", color = Muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(16.dp))
        if (history.isEmpty()) Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Glyph("focus", Modifier.size(38.dp), Sage); Spacer(Modifier.height(12.dp))
            Text("这一天还没有专注记录", color = Muted)
            Text("完成或结束专注后，会自动保存在这里。", color = Muted, style = MaterialTheme.typography.bodySmall)
        } else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(history, key = { it.id }) { record ->
                BoxWithConstraints(Modifier.fillMaxWidth().then(if(LocalAppearance.current.interfaceMaterial==InterfaceMaterial.GLASS) Modifier.background(Tomato.copy(alpha=.035f),SmallShape) else Modifier.background(Paper,SmallShape)).clickable { detail = record }.padding(16.dp)) {
                    val stacked = maxWidth < 400.dp || LocalDensity.current.fontScale >= 1.6f
                    val info: @Composable () -> Unit = {
                        Text(record.taskTitle, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        val time = DateTimeFormatter.ofPattern("HH:mm")
                        val zone = ZoneId.systemDefault()
                        Text("${Instant.ofEpochMilli(record.startedAtMs).atZone(zone).format(time)} — ${Instant.ofEpochMilli(record.endedAtMs).atZone(zone).format(time)} · ${if (record.completed) "完成一轮" else "提前结束"}",
                            color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    val duration: @Composable () -> Unit = {
                        Text(durationText(Statistics.forDay(listOf(record), selectedDate).focusMs),
                            color = if (record.completed) Sage else Muted, style = MaterialTheme.typography.labelLarge)
                    }
                    if (stacked) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { info(); duration() }
                    else Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { info() }; Spacer(Modifier.width(16.dp)); duration()
                    }
                }
            }
        }
    }
    }
    WidgetDesk("stats", fallback = {
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val wide = maxWidth >= 840.dp && androidx.compose.ui.platform.LocalDensity.current.fontScale < 1.6f
        Column(Modifier.widthIn(max = if (preset == UiPreset.MINIMAL) 860.dp else 1500.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(gap)) {
            SummaryCards(todayStats, dailyGoal)
            if (wide && preset in listOf(UiPreset.FLIP, UiPreset.JOURNAL)) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.Top) {
                    if (preset == UiPreset.JOURNAL) {
                        Column(Modifier.weight(1.4f)) { entries() }
                        Column(Modifier.weight(1f)) { trend() }
                    } else {
                        Column(Modifier.weight(1f)) { trend() }
                        Column(Modifier.weight(1.3f)) { entries() }
                    }
                }
            } else { trend(); entries() }
        }
    }
    }) { t -> when(t.id) {
        "summary" -> SummaryCards(todayStats,dailyGoal)
        "week" -> trend()
        "history" -> entries()
        "garden" -> GardenWidget()
        "heat" -> PaperCard(Modifier.fillMaxWidth()) {
            Text("四周热力图",style=MaterialTheme.typography.titleMedium)
            val days=remember(records,today,zone){(27 downTo 0).map {Statistics.forDay(records,today.minusDays(it.toLong()),zone)}}
            val max=days.maxOf{it.focusMs}.coerceAtLeast(60_000L)
            days.chunked(7).forEach {row->Row {row.forEach { day->val date=day.date
                Box(Modifier.weight(1f).heightIn(min=48.dp).padding(1.dp).background(Sage.copy(alpha=.08f+.64f*day.focusMs/max),SmallShape)
                    .clickable {selectedDateText=date.toString(); heatDetail=date.toString()}.semantics {contentDescription="$date，${day.focusMs/60000} 分钟"},contentAlignment=Alignment.Center){Text(date.dayOfMonth.toString(),style=MaterialTheme.typography.bodySmall)}
            }}}
        }
        "rank" -> PaperCard(Modifier.fillMaxWidth()) {
            Text("近七天 · 任务用时",style=MaterialTheme.typography.titleMedium)
            val ranking=remember(records,today,zone){records.groupBy {it.taskId}.map { (id, group)->
                (if(id==null) "自由专注" else group.maxBy{it.endedAtMs}.taskTitle) to (0..6).sumOf{Statistics.forDay(group,today.minusDays(it.toLong()),zone).focusMs}
            }.filter{it.second>0}.sortedByDescending{it.second}.take(5)}
            if(ranking.isEmpty()) Text("完成一次专注后，在这里回看。",color=Muted)
            ranking.forEachIndexed {i,(name,ms)->Text("${i+1}. $name　${durationText(ms)}",Modifier.padding(vertical=5.dp))}
        }
    } }
    heatDetail?.let { date -> FocusAlertDialog({heatDetail=null}, title={Text(date)},text={Column(Modifier.verticalScroll(rememberScrollState())){Text("${selectedStats.completedRounds} 轮 · ${durationText(selectedStats.focusMs)}");if(history.isEmpty()) Text("这一天还没有专注记录");history.forEach { Text(it.taskTitle) }}},confirmButton={FocusTextButton({heatDetail=null}){Text("关闭")}}) }
    detail?.let { record ->
        FocusAlertDialog(onDismissRequest = { detail = null }, title = { Text("专注记录") },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(record.taskTitle)
                Text("${durationText(record.focusedMs)} · ${if (record.completed) "完成一轮" else "提前结束"}", color = Muted)
            } }, confirmButton = { FocusTextButton(onClick = { detail = null }) { Text("关闭") } })
    }
    if (showDatePicker) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            yearRange = 2020..2100,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            })
        FocusDatePickerDialog(onDismissRequest = { showDatePicker = false },
            confirmButton = { FocusTextButton(onClick = { picker.selectedDateMillis?.let { selectedDateText = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }; showDatePicker = false }) { Text("确定") } },
            dismissButton = { FocusTextButton(onClick = { showDatePicker = false }) { Text("取消") } }) {
            DatePicker(state = picker, title = { Text("选择记录日期", Modifier.padding(start = 24.dp, top = 20.dp)) }, showModeToggle = false,
                colors = if(LocalAppearance.current.interfaceMaterial==InterfaceMaterial.GLASS) DatePickerDefaults.colors(
                    containerColor=Color.Transparent,selectedDayContainerColor=Tomato.copy(alpha=.18f),selectedDayContentColor=Ink,
                    selectedYearContainerColor=Tomato.copy(alpha=.18f),selectedYearContentColor=Ink,todayContentColor=Tomato) else DatePickerDefaults.colors())
        }
    }
}


@Composable
fun WeeklyChart(week: List<DayStats>, today: LocalDate, selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    val preset = LocalAppearance.current.uiPreset
    val maximum = week.maxOfOrNull { it.focusMs }?.coerceAtLeast(60_000) ?: 60_000
    val accent = Tomato
    val line = Line
    val muted = Muted
    val short = LocalAppearance.current.uiDensity == UiDensity.COMPACT
    fun date(day: DayStats) = if (day.date == today) "今天" else day.date.format(DateTimeFormatter.ofPattern("M.d"))
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    if (preset == UiPreset.JOURNAL || maxWidth < 560.dp || LocalDensity.current.fontScale >= 1.3f) {
        Column(verticalArrangement = Arrangement.spacedBy(if (short) 4.dp else 12.dp)) {
            week.forEach { day ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(day.date == selected) { onSelect(day.date) }.padding(vertical = 8.dp)
                    .semantics { contentDescription = "${day.date}，专注 ${day.focusMs / 60_000} 分钟，点击查看记录" },
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(date(day), Modifier.width(if (LocalDensity.current.fontScale >= 1.6f) 88.dp else 56.dp), color = if (day.date == selected) accent else muted,
                        style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(progress = { (day.focusMs.toFloat() / maximum).coerceIn(0f, 1f) },
                        modifier = Modifier.weight(1f).height(5.dp), color = accent, trackColor = line)
                    Text("${day.focusMs / 60_000}分", Modifier.widthIn(min = 48.dp).padding(start = 8.dp),
                        color = muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    } else {
        if (preset == UiPreset.MINIMAL) Canvas(Modifier.fillMaxWidth().height(if (short) 70.dp else 110.dp)) {
            drawLine(line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            val points = week.mapIndexed { i, day -> Offset(size.width * (i + 0.5f) / week.size,
                size.height * (0.92f - 0.82f * day.focusMs.toFloat() / maximum)) }
            val path = Path().apply { points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
            drawPath(path, accent, style = Stroke(2.dp.toPx()))
            points.forEach { drawCircle(accent, 3.dp.toPx(), it) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            week.forEach { day ->
                val highlighted = day.date == selected
                Column(Modifier.weight(1f).heightIn(min = 48.dp).selectable(day.date == selected) { onSelect(day.date) }
                    .semantics { contentDescription = "${day.date}，专注 ${day.focusMs / 60_000} 分钟，点击查看记录" },
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${day.focusMs / 60_000}", color = if (highlighted) accent else muted, style = MaterialTheme.typography.bodySmall)
                    if (preset != UiPreset.MINIMAL) {
                        Spacer(Modifier.height(8.dp))
                        val h = if (short) 82f else 120f
                        Box(Modifier.height((h + 4).dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                            Box(Modifier.widthIn(max = if (preset == UiPreset.FLIP) 54.dp else 42.dp).fillMaxWidth(0.7f)
                                .height((4 + h * day.focusMs.toFloat() / maximum).dp)
                                .background(if (highlighted) accent else accent.copy(alpha = 0.35f),
                                    RoundedCornerShape(topStart = if (preset == UiPreset.FLIP) 2.dp else 8.dp,
                                        topEnd = if (preset == UiPreset.FLIP) 2.dp else 8.dp)))
                        }
                    }
                    Text(date(day), Modifier.padding(vertical = 12.dp), color = if (highlighted) accent else muted,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

}
