package app.yike.focus.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.*
import androidx.compose.ui.zIndex
import app.yike.focus.data.FocusRepository
import app.yike.focus.domain.*
import kotlin.math.*
import kotlinx.coroutines.launch
import org.json.JSONObject

internal val LocalDeskRepo = staticCompositionLocalOf<FocusRepository?> { null }
internal val LocalDeskScroll = compositionLocalOf<ScrollState?> { null }
internal val widgetNames =
    mapOf(
        "timer" to "专注时钟",
        "tasks" to "今天想完成",
        "summary" to "今日摘要",
        "week" to "最近七天",
        "history" to "专注记录",
        "next" to "下一件任务",
        "audio" to "背景声",
        "heat" to "四周热力图",
        "rank" to "任务用时排行",
        "garden" to "我的花房",
    )

internal data class Tile(val id: String, val w: Int = 2, val h: Int = 2)

internal fun encodeTiles(items: List<Tile>) = items.joinToString(";") { "${it.id},${it.w},${it.h}" }

internal fun decodeTiles(text: String, allowed: List<String>) =
    text
        .split(';')
        .mapNotNull { row ->
            val v = row.split(',')
            if (v.size != 3 || v[0] !in allowed) null
            else
                Tile(
                    v[0],
                    v[1].toIntOrNull()?.coerceIn(1, 4) ?: 2,
                    v[2].toIntOrNull()?.coerceIn(1, 3) ?: 2,
                )
        }
        .distinctBy { it.id }

private fun columns(width: Dp, font: Float) =
    when {
        width / font.coerceAtLeast(1f) >= 880.dp -> 4
        width / font.coerceAtLeast(1f) >= 540.dp -> 2
        else -> 1
    }

internal fun slots(items: List<Tile>, cols: Int): List<Pair<Int, Int>> {
    val used = mutableSetOf<Pair<Int, Int>>()
    var start = 0
    return items.map { t ->
        val w = t.w.coerceAtMost(cols)
        var i = start
        while (true) {
            val x = i % cols
            val y = i / cols
            if (
                x + w <= cols &&
                    (0 until w).all { dx ->
                        (0 until t.h).all { dy -> (x + dx to y + dy) !in used }
                    }
            )
                break
            i++
        }
        val x = i % cols
        val y = i / cols
        for (dx in 0 until w) for (dy in 0 until t.h) used.add(x + dx to y + dy)
        start = i + 1
        x to y
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WidgetDesk(
    page: String,
    fallback: @Composable () -> Unit,
    emergency: @Composable () -> Unit = {},
    tile: @Composable (Tile) -> Unit,
) {
    val repo = LocalDeskRepo.current
    if (repo == null) {
        fallback()
        return
    }
    val raw by repo.workspace.collectAsState(initial = "{}")
    val water by repo.gardenUsed.collectAsState(initial = emptySet())
    val data = remember(raw) { JSONObject(raw) }
    val garden = data.optBoolean("discovered") || water.isNotEmpty()
    val available =
        (if (page == "focus") listOf("timer", "tasks", "summary", "next", "audio")
        else listOf("summary", "week", "history", "heat", "rank")) +
            if (garden) listOf("garden") else emptyList()
    val preset = LocalAppearance.current.uiPreset
    val defaults =
        if (page == "focus")
            (if (preset == UiPreset.FLIP) listOf(Tile("tasks"), Tile("timer"))
            else listOf(Tile("timer"), Tile("tasks"))) + Tile("summary", 4, 1)
        else listOf(Tile("summary", 4, 1), Tile("week", 2, 2), Tile("history", 2, 2))
    val saved = data.optString(page, "default")
    var editing by rememberSaveable(page) { mutableStateOf(false) }
    var draft by rememberSaveable(page) { mutableStateOf("") }
    var library by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf<String?>(null) }
    var reset by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val items =
        if (editing) decodeTiles(draft, available)
        else if (saved == "default") defaults else decodeTiles(saved, available)
    val scope = rememberCoroutineScope()
    fun enter() {
        draft = encodeTiles(items)
        reset = false
        editing = true
    }
    fun update(list: List<Tile>) {
        draft = encodeTiles(list)
        reset = false
    }
    BackHandler(editing && !library && menu == null) { editing = false }
    Column(
        Modifier.fillMaxWidth()
            .then(
                if (!editing)
                    Modifier.pointerInput(items) { detectTapGestures(onLongPress = { enter() }) }
                else Modifier
            )
    ) {
        if (!editing) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                FocusTextButton({ enter() }, modifier = Modifier.testTag("edit_$page")) {
                    Text("编辑布局")
                }
            }
            if (saved == "default") fallback()
            else {
                if (page == "focus" && items.none { it.id == "timer" }) emergency()
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val n = columns(maxWidth, LocalDensity.current.fontScale)
                    WidgetGrid(items, n, tile)
                }
                if (items.isEmpty())
                    FocusOutlinedButton({
                        enter()
                        library = true
                    }) {
                        Text("添加组件")
                    }
            }
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FocusButton({ library = true }) { Text("添加组件") }
                FocusTextButton({
                    draft = encodeTiles(defaults)
                    reset = true
                }) {
                    Text("恢复预设")
                }
                FocusTextButton({ editing = false }, enabled = !busy) { Text("取消") }
                FocusTonalButton(
                    {
                        busy = true
                        scope.launch {
                            try {
                                repo.reviseWorkspace {
                                    if (reset) it.remove(page) else it.put(page, draft)
                                }
                                editing = false
                            } catch (_: java.io.IOException) {
                                error = true
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy,
                ) {
                    Text("完成")
                }
            }
            if (error) Text("布局保存失败，请重试。")
            EditableGrid(items, ::update, { menu = it }, tile)
        }
    }
    if (library)
        FocusAlertDialog(
            { library = false },
            title = { Text("组件库") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    available.forEach { id ->
                        val added = items.any { it.id == id }
                        FocusTextButton(
                            {
                                update(items + Tile(id))
                                library = false
                            },
                            enabled = !added,
                        ) {
                            Text(widgetNames.getValue(id) + if (added) " · 已添加" else "")
                        }
                    }
                }
            },
            confirmButton = { FocusTextButton({ library = false }) { Text("关闭") } },
        )
    menu?.let { id ->
        val ix = items.indexOfFirst { it.id == id }
        if (ix >= 0) {
            val t = items[ix]
            fun modify(list: List<Tile>) {
                update(list)
                menu = null
            }
            FocusAlertDialog(
                { menu = null },
                title = { Text(widgetNames.getValue(id)) },
                text = {
                    FlowRow {
                        listOf(-1, 1).forEach { v ->
                            FocusTextButton(
                                {
                                    modify(
                                        items.toMutableList().apply { add(ix + v, removeAt(ix)) }
                                    )
                                },
                                enabled = ix + v in items.indices,
                            ) {
                                Text(if (v < 0) "前移" else "后移")
                            }
                        }
                        listOf(
                                "加宽" to t.copy(w = t.w + 1),
                                "变窄" to t.copy(w = t.w - 1),
                                "加高" to t.copy(h = t.h + 1),
                                "降低" to t.copy(h = t.h - 1),
                            )
                            .forEach { (label, next) ->
                                FocusTextButton(
                                    { modify(items.map { if (it.id == id) next else it }) },
                                    enabled = next.w in 1..4 && next.h in 1..3,
                                ) {
                                    Text(label)
                                }
                            }
                        FocusTextButton({ modify(items.filterNot { it.id == id }) }) {
                            Text("移除组件")
                        }
                    }
                },
                confirmButton = { FocusTextButton({ menu = null }) { Text("关闭") } },
            )
        }
    }
}

@Composable
private fun EditableGrid(
    items: List<Tile>,
    update: (List<Tile>) -> Unit,
    menu: (String) -> Unit,
    tile: @Composable (Tile) -> Unit,
) {
    val density = LocalDensity.current
    val scroll = LocalDeskScroll.current
    val view = LocalView.current
    val latest by rememberUpdatedState(items)
    val change by rememberUpdatedState(update)
    var active by remember { mutableStateOf<String?>(null) }
    var drag by remember { mutableStateOf(Offset.Zero) }
    var anchor by remember { mutableStateOf(Offset.Zero) }
    var grab by remember { mutableStateOf(Offset.Zero) }
    var startScroll by remember { mutableIntStateOf(0) }
    var screenY by remember { mutableFloatStateOf(0f) }
    var gridY by remember { mutableFloatStateOf(0f) }
    val duration = motionMillis(180)
    BoxWithConstraints(
        Modifier.fillMaxWidth().onGloballyPositioned { gridY = it.positionInWindow().y }
    ) {
        val n = columns(maxWidth, density.fontScale)
        val gap = 8.dp
        val step = (maxWidth + gap) / n
        val row = (96 * density.fontScale.coerceIn(1f, 2f)).dp
        val screenX = remember { mutableFloatStateOf(0f) }
        val relax = remember { androidx.compose.animation.core.Animatable(0f) }
        val scope = rememberCoroutineScope()
        var settling by remember { mutableStateOf<String?>(null) }
        var releaseOffset by remember { mutableStateOf(Offset.Zero) }
        var initialOrder by remember { mutableStateOf(emptyList<Tile>()) }
        fun reorderUnderPointer() {
            val id = active ?: return
            val layout = latest
            val positions = slots(layout, n)
            val x = screenX.floatValue
            val y = screenY - gridY
            val target =
                positions.indices.firstOrNull { i ->
                    val (cx, cy) = positions[i]
                    with(density) {
                        x >= cx * step.toPx() &&
                            x < (cx + layout[i].w.coerceAtMost(n)) * step.toPx() &&
                            y >= cy * row.toPx() &&
                            y < (cy + layout[i].h) * row.toPx()
                    }
                } ?: return
            val from = layout.indexOfFirst { it.id == id }
            if (from >= 0 && target != from)
                change(layout.toMutableList().apply { add(target, removeAt(from)) })
        }
        LaunchedEffect(active, n) {
            if (active == null || scroll == null) return@LaunchedEffect
            var previous = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val elapsed = ((now - previous) / 1e9f).coerceIn(0f, .05f)
                previous = now
                val edge = with(density) { 96.dp.toPx() }
                val direction =
                    when {
                        screenY < edge -> (screenY - edge) / edge
                        screenY > view.height - edge -> (screenY - view.height + edge) / edge
                        else -> 0f
                    }
                if (
                    direction != 0f &&
                        scroll.scrollBy(direction.coerceIn(-1f, 1f) * edge * 6 * elapsed) != 0f
                )
                    reorderUnderPointer()
            }
        }
        val spots = slots(items, n)
        val rows = items.indices.maxOfOrNull { spots[it].second + items[it].h } ?: 1
        Box(Modifier.fillMaxWidth().height(row * rows + 8.dp)) {
            items.forEachIndexed { i, t ->
                key(t.id) {
                    val x = step * spots[i].first
                    val y = row * spots[i].second
                    val ax by animateDpAsState(x, tween(duration), label = "tile_x")
                    val ay by animateDpAsState(y, tween(duration), label = "tile_y")
                    val moving = active == t.id
                    Box(
                        Modifier.zIndex(if (moving) 2f else 0f)
                            .width(step * t.w.coerceAtMost(n) - gap)
                            .height(row * t.h - gap)
                            .graphicsLayer {
                                translationX =
                                    when {
                                        moving -> anchor.x + drag.x
                                        settling == t.id -> x.toPx() + releaseOffset.x * relax.value
                                        else -> ax.toPx()
                                    }
                                translationY =
                                    when {
                                        moving ->
                                            anchor.y + drag.y + (scroll?.value ?: 0) - startScroll
                                        settling == t.id -> y.toPx() + releaseOffset.y * relax.value
                                        else -> ay.toPx()
                                    }
                            }
                            .background(if (moving) Peach else CardPaper, PanelShape)
                            .border(1.dp, if (moving) Tomato else Line, PanelShape)
                            .pointerInput(t.id, n, step, row) {
                                detectDragGestures(
                                    onDragStart = { point ->
                                        val index = latest.indexOfFirst { it.id == t.id }
                                        val pos = slots(latest, n)[index]
                                        anchor =
                                            Offset(step.toPx() * pos.first, row.toPx() * pos.second)
                                        initialOrder = latest
                                        drag = Offset.Zero
                                        grab = point
                                        screenX.floatValue = anchor.x + point.x
                                        screenY = gridY + anchor.y + point.y
                                        startScroll = scroll?.value ?: 0
                                        settling = null
                                        active = t.id
                                    },
                                    onDragEnd = {
                                        val pos =
                                            slots(latest, n)[latest.indexOfFirst { it.id == t.id }]
                                        releaseOffset =
                                            Offset(
                                                anchor.x + drag.x - pos.first * step.toPx(),
                                                anchor.y + drag.y + (scroll?.value ?: 0) -
                                                    startScroll -
                                                    pos.second * row.toPx(),
                                            )
                                        active = null
                                        if (duration > 0)
                                            scope.launch {
                                                relax.snapTo(1f)
                                                settling = t.id
                                                relax.animateTo(0f, tween(duration))
                                                if (settling == t.id) settling = null
                                            }
                                    },
                                    onDragCancel = {
                                        active = null
                                        change(initialOrder)
                                    },
                                ) { event, delta ->
                                    event.consume()
                                    drag += delta
                                    screenY += delta.y
                                    screenX.floatValue = anchor.x + drag.x + grab.x
                                    reorderUnderPointer()
                                }
                            }
                            .testTag("tile_${t.id}")
                    ) {
                        if (t.h > 1)
                            Miniature(
                                Modifier.matchParentSize()
                                    .padding(
                                        start = 10.dp,
                                        end = 10.dp,
                                        top = 46.dp,
                                        bottom = 28.dp,
                                    )
                            ) {
                                tile(t)
                            }
                        Box(
                            Modifier.matchParentSize().pointerInput(Unit) {
                                awaitPointerEventScope { while (true) awaitPointerEvent() }
                            }
                        )
                        FocusTextButton(
                            { menu(t.id) },
                            modifier = Modifier.align(Alignment.TopStart),
                        ) {
                            Text(widgetNames.getValue(t.id))
                        }
                        Text(
                            "${t.w} × ${t.h}",
                            Modifier.align(Alignment.BottomStart).padding(7.dp),
                            color = Muted,
                        )
                        Box(
                            Modifier.align(Alignment.BottomEnd)
                                .testTag("resize_${t.id}")
                                .size(48.dp)
                                .clickable { menu(t.id) }
                                .pointerInput(t.id, n, step, row) {
                                    var initial = t
                                    var sum = Offset.Zero
                                    detectDragGestures(
                                        onDragStart = {
                                            initial = latest.first { it.id == t.id }
                                            sum = Offset.Zero
                                        }
                                    ) { e, delta ->
                                        e.consume()
                                        sum += delta
                                        val next =
                                            initial.copy(
                                                w =
                                                    (initial.w + (sum.x / step.toPx()).roundToInt())
                                                        .coerceIn(1, 4),
                                                h =
                                                    (initial.h + (sum.y / row.toPx()).roundToInt())
                                                        .coerceIn(1, 3),
                                            )
                                        if (latest.first { it.id == t.id } != next)
                                            change(latest.map { if (it.id == t.id) next else it })
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("↘", color = Tomato)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Use the same occupancy model in edit and display. Grow tracks to fit actual text at this font
 * size.
 */
@Composable
private fun WidgetGrid(items: List<Tile>, columns: Int, content: @Composable (Tile) -> Unit) {
    androidx.compose.ui.layout.Layout(
        content = { items.forEach { item -> key(item.id) { Box(propagateMinConstraints = true) { content(item) } } } }
    ) { children, constraints ->
        val gap = 12.dp.roundToPx()
        val unit = (constraints.maxWidth + gap).toFloat() / columns
        val positions = slots(items, columns)
        val tracks =
            IntArray(items.indices.maxOfOrNull { positions[it].second + items[it].h } ?: 0) {
                94.dp.roundToPx()
            }
        val placeables =
            children.mapIndexed { index, child ->
                val item = items[index]
                val width = (unit * item.w.coerceAtMost(columns)).roundToInt() - gap
                val cell =
                    child.measure(
                        Constraints(
                            minWidth = width,
                            maxWidth = width,
                            minHeight = (94.dp.roundToPx() + gap) * item.h - gap,
                        )
                    )
                val y = positions[index].second
                val need = ceil((cell.height - gap * (item.h - 1)).toDouble() / item.h).toInt()
                for (row in y until y + item.h) tracks[row] = max(tracks[row], need)
                cell
            }
        val tops = IntArray(tracks.size + 1)
        for (i in tracks.indices) tops[i + 1] = tops[i] + tracks[i] + gap
        layout(constraints.maxWidth, (tops.last() - gap).coerceAtLeast(0)) {
            placeables.forEachIndexed { i, cell ->
                cell.placeRelative(
                    (positions[i].first * unit).roundToInt(),
                    tops[positions[i].second],
                )
            }
        }
    }
}

@Composable
private fun Miniature(modifier: Modifier, content: @Composable () -> Unit) {
    androidx.compose.ui.layout.Layout(
        modifier = modifier.clip(SmallShape).clearAndSetSemantics {},
        content = { Box { content() } },
    ) { children, c ->
        val width = c.maxWidth.coerceAtLeast(360.dp.roundToPx())
        val placeable = children.single().measure(Constraints.fixedWidth(width))
        val scale =
            min(
                    c.maxWidth.toFloat() / width,
                    c.maxHeight.toFloat() / placeable.height.coerceAtLeast(1),
                )
                .coerceAtMost(1f)
        layout(c.maxWidth, c.maxHeight) {
            placeable.placeWithLayer(((c.maxWidth - width * scale) / 2).roundToInt(), 0) {
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                scaleX = scale
                scaleY = scale
            }
        }
    }
}
