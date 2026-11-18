package app.yike.focus.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
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
    val interaction = remember(page) { CardEditor() }
    BackHandler(editing && !library && menu == null) { editing = false }
    LaunchedEffect(editing) { if (!editing) interaction.select(null) }
    Column(
        Modifier.fillMaxWidth()
            .then(
                if (!editing)
                    Modifier.pointerInput(items) { detectTapGestures(onLongPress = { enter() }) }
                else Modifier
            )
    ) {
        Box(Modifier.fillMaxWidth().heightIn(min = 52.dp), contentAlignment = Alignment.CenterEnd) {
            if (!editing)
                FocusTextButton({ enter() }, modifier = Modifier.testTag("edit_$page")) {
                    Text("编辑布局")
                }
            else
                BoxWithConstraints {
                    val compact = maxWidth / LocalDensity.current.fontScale < 610.dp
                    var more by remember { mutableStateOf(false) }
                    val resetLayout = {
                        draft = encodeTiles(defaults)
                        reset = true
                        interaction.select(null)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FocusTextButton({ library = true }) { Text("添加组件") }
                        if (compact)
                            Box {
                                FocusTextButton({ more = true }) { Text("更多") }
                                FocusDropdownMenu(more, { more = false }) {
                                    interaction.selected?.let { id ->
                                        FocusMenuItem(
                                            { Text("调整") },
                                            {
                                                more = false
                                                menu = id
                                            },
                                        )
                                        FocusMenuItem(
                                            { Text("移除") },
                                            {
                                                more = false
                                                update(items.filterNot { it.id == id })
                                                interaction.select(null)
                                            },
                                        )
                                    }
                                    FocusMenuItem(
                                        { Text("恢复预设") },
                                        {
                                            more = false
                                            resetLayout()
                                        },
                                    )
                                }
                            }
                        else {
                            interaction.selected?.let { id ->
                                FocusTextButton({ menu = id }) { Text("调整") }
                                FocusTextButton({
                                    update(items.filterNot { it.id == id })
                                    interaction.select(null)
                                }) {
                                    Text("移除")
                                }
                            }
                            FocusTextButton(resetLayout) { Text("恢复预设") }
                        }
                        FocusTextButton({ editing = false }, enabled = !busy) { Text("取消") }
                        FocusTextButton(
                            {
                                busy = true
                                scope.launch {
                                    try {
                                        repo.reviseWorkspace {
                                            if (
                                                reset ||
                                                    saved == "default" &&
                                                        draft == encodeTiles(defaults)
                                            )
                                                it.remove(page)
                                            else it.put(page, draft)
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
                }
        }
        if (error && editing) Text("布局保存失败，请重试。")
        val originalLayout =
            if (editing) reset || saved == "default" && draft == encodeTiles(defaults)
            else saved == "default"
        DeskSurface(interaction, editing, items, ::update, { menu = it }) {
            if (originalLayout) fallback()
            else {
                if (!editing && page == "focus" && items.none { it.id == "timer" }) emergency()
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    WidgetGrid(items, columns(maxWidth, LocalDensity.current.fontScale)) { item ->
                        DeskCard(item.id) { tile(item) }
                    }
                }
                if (items.isEmpty() && !editing)
                    FocusOutlinedButton({
                        enter()
                        library = true
                    }) {
                        Text("添加组件")
                    }
            }
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

private class CardEditor {
    var selected by mutableStateOf<String?>(null)
        private set

    fun select(id: String?) {
        selected = id
    }

    val bounds = mutableStateMapOf<String, Rect>()
    var dragged by mutableStateOf<String?>(null)
    var finger by mutableStateOf(Offset.Zero)
    var grip = Offset.Zero
    var downPoint = Offset.Zero
    var rootOrigin = Offset.Zero
    var resizing = false

    fun hit(point: Offset) = bounds.entries.firstOrNull { it.value.contains(point) }?.key

    fun inCorner(id: String, point: Offset, hitSize: Float): Boolean {
        val rect = bounds[id] ?: return false
        return point.x >= rect.right - hitSize && point.y >= rect.bottom - hitSize
    }
}

private data class CardMode(
    val state: CardEditor,
    val editing: Boolean,
    val items: List<Tile>,
    val actions: (String) -> Unit,
)

private val LocalCardMode = compositionLocalOf<CardMode?> { null }

@Composable
private fun DeskSurface(
    editor: CardEditor,
    editing: Boolean,
    items: List<Tile>,
    update: (List<Tile>) -> Unit,
    menu: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    val current by rememberUpdatedState(items)
    val onUpdate by rememberUpdatedState(update)
    val scroll = LocalDeskScroll.current
    val view = LocalView.current
    val density = LocalDensity.current
    fun reorder() {
        val id = editor.dragged ?: return
        val target = editor.hit(editor.finger) ?: return
        val from = current.indexOfFirst { it.id == id }
        val to = current.indexOfFirst { it.id == target }
        if (from >= 0 && to >= 0 && from != to)
            onUpdate(current.toMutableList().apply { add(to, removeAt(from)) })
    }
    LaunchedEffect(editor.dragged, editing) {
        if (!editing || editor.dragged == null || editor.resizing || scroll == null)
            return@LaunchedEffect
        val edge = with(density) { 92.dp.toPx() }
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1e9f).coerceAtMost(.05f)
            last = now
            val y = editor.finger.y
            val direction =
                when {
                    y < edge -> (y - edge) / edge
                    y > view.height - edge -> (y - view.height + edge) / edge
                    else -> 0f
                }
            if (
                direction != 0f &&
                    scroll.scrollBy(direction.coerceIn(-1f, 1f) * edge * 6 * dt) != 0f
            )
                reorder()
        }
    }
    BoxWithConstraints(
        Modifier.fillMaxWidth().onGloballyPositioned {
            editor.rootOrigin = it.localToWindow(Offset.Zero)
        }
    ) {
        val n = columns(maxWidth, density.fontScale)
        val step = with(density) { (maxWidth / n).toPx() }
        val row = with(density) { 96.dp.toPx() }
        Box(
            Modifier.fillMaxWidth()
                .then(
                    if (editing)
                        Modifier.pointerInput(editor) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event =
                                            awaitPointerEvent(
                                                androidx.compose.ui.input.pointer.PointerEventPass
                                                    .Initial
                                            )
                                        event.changes
                                            .firstOrNull { it.pressed && !it.previousPressed }
                                            ?.let {
                                                editor.downPoint = editor.rootOrigin + it.position
                                            }
                                    }
                                }
                            }
                            .pointerInput(editor, n) {
                                var initial = listOf<Tile>()
                                var original: Tile? = null
                                var start = Offset.Zero
                                detectDragGestures(
                                    onDragStart = { point ->
                                        start = editor.downPoint
                                        val id = editor.hit(start)
                                        if (id != null) {
                                            initial = current
                                            original = current.firstOrNull { it.id == id }
                                            editor.resizing =
                                                editor.selected == id &&
                                                    editor.inCorner(id, start, 48.dp.toPx())
                                            editor.select(id)
                                            editor.grip = start - editor.bounds.getValue(id).topLeft
                                            editor.finger = start
                                            editor.dragged = id
                                        }
                                    },
                                    onDragEnd = {
                                        editor.dragged = null
                                        editor.resizing = false
                                    },
                                    onDragCancel = {
                                        if (editor.dragged != null) onUpdate(initial)
                                        editor.dragged = null
                                        editor.resizing = false
                                    },
                                ) { event, delta ->
                                    val t = original
                                    if (editor.dragged != null && t != null) {
                                        event.consume()
                                        editor.finger += delta
                                        if (editor.resizing) {
                                            val diff = editor.finger - start
                                            val next =
                                                t.copy(
                                                    w =
                                                        (t.w + (diff.x / step).roundToInt())
                                                            .coerceIn(1, 4),
                                                    h =
                                                        (t.h + (diff.y / row).roundToInt())
                                                            .coerceIn(1, 3),
                                                )
                                            if (current.firstOrNull { it.id == t.id } != next)
                                                onUpdate(
                                                    current.map { if (it.id == t.id) next else it }
                                                )
                                        } else reorder()
                                    }
                                }
                            }
                    else Modifier
                )
        ) {
            CompositionLocalProvider(
                LocalCardMode provides CardMode(editor, editing, items, menu)
            ) {
                content()
            }
        }
    }
}

@Composable
internal fun DeskCard(id: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val mode = LocalCardMode.current
    val editor = mode?.state
    // Track untransformed bounds: selection scales only the drawing, not the drop targets.
    val selected = mode?.editing == true && editor?.selected == id
    val scale by
        animateFloatAsState(
            if (selected) .98f else 1f,
            tween(motionMillis(130)),
            label = "card_selection",
        )
    DisposableEffect(editor, id) { onDispose { editor?.bounds?.remove(id) } }
    Box(
        modifier
            .fillMaxWidth()
            .testTag("tile_$id")
            .onGloballyPositioned { coordinates ->
                val origin = coordinates.localToWindow(Offset.Zero)
                editor
                    ?.bounds
                    ?.set(
                        id,
                        Rect(
                            origin,
                            androidx.compose.ui.geometry.Size(
                                coordinates.size.width.toFloat(),
                                coordinates.size.height.toFloat(),
                            ),
                        ),
                    )
            }
            .zIndex(if (editor?.dragged == id) 1f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = 0f
                translationY = 0f
                val bounds = editor?.bounds?.get(id)
                if (editor != null && bounds != null && editor.dragged == id && !editor.resizing) {
                    val distance = editor.finger - editor.grip - bounds.topLeft
                    translationX = distance.x
                    translationY = distance.y
                }
            },
        propagateMinConstraints = true,
    ) {
        Box(
            Modifier.then(
                if (mode?.editing == true) Modifier.clearAndSetSemantics {} else Modifier
            ),
            propagateMinConstraints = true,
        ) {
            content()
        }
        if (mode != null && mode.editing) {
            val t = mode.items.firstOrNull { it.id == id }
            Box(
                Modifier.matchParentSize()
                    .clickable(
                        interactionSource =
                            remember {
                                androidx.compose.foundation.interaction.MutableInteractionSource()
                            },
                        indication = null,
                    ) {
                        mode.state.select(id)
                    }
                    .semantics {
                        contentDescription = widgetNames.getValue(id)
                        this.selected = selected
                        stateDescription = "${t?.w ?: 2} × ${t?.h ?: 2}"
                        customActions =
                            listOf(
                                CustomAccessibilityAction("调整位置和大小") {
                                    mode.state.select(id)
                                    mode.actions(id)
                                    true
                                }
                            )
                    }
            )
            if (selected)
                Box(Modifier.matchParentSize()) {
                    Spacer(Modifier.align(Alignment.BottomEnd).size(48.dp).testTag("resize_$id"))
                }
        }
    }
}

@Composable
private fun WidgetGrid(items: List<Tile>, columns: Int, content: @Composable (Tile) -> Unit) {
    androidx.compose.ui.layout.Layout(
        content = {
            items.forEach { item ->
                key(item.id) { Box(propagateMinConstraints = true) { content(item) } }
            }
        }
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
