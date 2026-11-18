package app.yike.focus.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import app.yike.focus.domain.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

internal fun allowedDock(dock: NavDock, widthDp: Float, fontScale: Float): NavDock =
    if ((widthDp < 600 || fontScale >= 1.6f) && dock in listOf(NavDock.LEFT, NavDock.RIGHT)) NavDock.BOTTOM else dock
internal fun nearestDock(center: Offset, width: Float, height: Float, sides: Boolean): NavDock =
    listOf(NavDock.TOP to center.y, NavDock.BOTTOM to height - center.y).let {
        if (sides) it + listOf(NavDock.LEFT to center.x, NavDock.RIGHT to width - center.x) else it
    }.minBy { it.second }.first

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun GlassAppScaffold(settings: FocusSettings, page: Int, today: LocalDate,
    snackbar: SnackbarHostState, onPage: (Int) -> Unit, dock: NavDock, onDock: (NavDock) -> Unit,
    content: @Composable ColumnScope.() -> Unit) {
    FocusBackdropHost(settings, enabled = true) {
        val layer = rememberGraphicsLayer()
        val source = remember(layer) { GlassSource(layer) }
        val density = LocalDensity.current
        var temporaryDock by remember(dock) { mutableStateOf(dock) }
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
            val effective = allowedDock(temporaryDock, maxWidth.value, density.fontScale)
            val vertical = effective == NavDock.LEFT || effective == NavDock.RIGHT
            val thickness = (76 + 24 * (density.fontScale - 1).coerceAtLeast(0f)).dp
            val padding = if (settings.uiDensity == UiDensity.COMPACT) 16.dp else 24.dp
            val top = if (effective == NavDock.TOP) thickness + 24.dp else 0.dp
            val bottom = if (effective == NavDock.BOTTOM) thickness + 24.dp else 0.dp
            val left = if (effective == NavDock.LEFT) 112.dp else 0.dp
            val right = if (effective == NavDock.RIGHT) 112.dp else 0.dp
            Box(Modifier.fillMaxSize().recordGlassSource(source, LocalGlassBackground.current)) {
                BoxWithConstraints(Modifier.fillMaxSize().padding(start = left, end = right), contentAlignment = Alignment.TopCenter) {
                    CompositionLocalProvider(LocalContentHeight provides (maxHeight - padding * 2 - top - bottom)) {
                        key(page) {
                            val viewportScroll = rememberScrollState()
                            Column(Modifier.widthIn(max = 1360.dp).fillMaxWidth().verticalScroll(viewportScroll)
                                // Navigation floats over the full scroll viewport. Keep end clearance
                                // inside the scroll content, never as a strip that clips the cards.
                                .padding(start = padding, end = padding, top = padding + top, bottom = padding + bottom)
                                .testTag("page_scroll")) {
                                WallpaperContent(enabled = true) {
                                    FlowRow(Modifier.fillMaxWidth().testTag("page_header"), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("一刻专注", style = MaterialTheme.typography.titleSmall, color = Muted)
                                        Text(today.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.SIMPLIFIED_CHINESE)),
                                            style = MaterialTheme.typography.bodySmall, color = Muted)
                                    }
                                }
                                Spacer(Modifier.height(16.dp))
                                CompositionLocalProvider(LocalDialogBackdrop provides source, LocalDeskScroll provides viewportScroll) { content() }
                                Spacer(Modifier.height(24.dp))
                            }
                        }
                    }
                }
            }
            val widthPx = with(density) { maxWidth.toPx() }; val heightPx = with(density) { maxHeight.toPx() }
            val marginPx = with(density) { 12.dp.toPx() }
            CompositionLocalProvider(LocalGlassContent provides source) {
                val alignment = when (effective) { NavDock.TOP -> Alignment.TopCenter; NavDock.BOTTOM -> Alignment.BottomCenter
                    NavDock.LEFT -> Alignment.CenterStart; NavDock.RIGHT -> Alignment.CenterEnd }
                FloatingGlassNav(page, vertical, Modifier.align(alignment).padding(12.dp), thickness, onPage,
                    effective, Offset(widthPx, heightPx),
                    onDock = { delta ->
                        val center = when (effective) {
                            NavDock.TOP -> Offset(widthPx / 2, with(density) { thickness.toPx() } / 2 + marginPx)
                            NavDock.BOTTOM -> Offset(widthPx / 2, heightPx - with(density) { thickness.toPx() } / 2 - marginPx)
                            NavDock.LEFT -> Offset(with(density) { 56.dp.toPx() }, heightPx / 2)
                            NavDock.RIGHT -> Offset(widthPx - with(density) { 56.dp.toPx() }, heightPx / 2)
                        } + delta
                        temporaryDock = nearestDock(center, widthPx, heightPx, maxWidth >= 600.dp && density.fontScale < 1.6f)
                        onDock(temporaryDock)
                    }, positionActions = NavDock.entries.filter { allowedDock(it, maxWidth.value, density.fontScale) == it }.map { target ->
                        CustomAccessibilityAction("移到${target.title}") { temporaryDock = target; onDock(target); true }
                    })
            }
            FocusSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = bottom))
        }
    }
}

@Composable private fun FloatingGlassNav(page: Int, vertical: Boolean, modifier: Modifier,
    thickness: Dp, onPage: (Int) -> Unit, dock: NavDock, viewport: Offset, onDock: (Offset) -> Unit,
    positionActions: List<CustomAccessibilityAction>) {
    val density = LocalDensity.current
    val scope=rememberCoroutineScope()
    val touch = rememberGlassInteraction()
    var shift by remember { mutableStateOf(Offset.Zero) }
    var selection by remember { mutableStateOf<Float?>(null) }
    var mode by remember { mutableIntStateOf(0) }
    var extent by remember { mutableFloatStateOf(1f) }
    var measured by remember { mutableStateOf(IntSize.Zero) }
    val margin = with(density) { 12.dp.toPx() }
    val currentViewport by rememberUpdatedState(viewport)
    val currentDock by rememberUpdatedState(dock)
    fun boundedDrag(value: Offset): Offset {
        val width = measured.width.toFloat(); val height = measured.height.toFloat()
        val startX = when (currentDock) { NavDock.LEFT -> margin; NavDock.RIGHT -> currentViewport.x-width-margin; else -> (currentViewport.x-width)/2 }
        val startY = when (currentDock) { NavDock.TOP -> margin; NavDock.BOTTOM -> currentViewport.y-height-margin; else -> (currentViewport.y-height)/2 }
        return Offset(value.x.coerceIn(minOf(0f,margin-startX), maxOf(0f,currentViewport.x-width-margin-startX)),
            value.y.coerceIn(minOf(0f,margin-startY), maxOf(0f,currentViewport.y-height-margin-startY)))
    }
    val onPageLatest by rememberUpdatedState(onPage); val onDockLatest by rememberUpdatedState(onDock)
    val pageLatest by rememberUpdatedState(page)
    val motion = motionMillis(200)
    val settled=remember {Animatable(page.toFloat())}
    LaunchedEffect(page) {if(selection==null) settled.animateTo(page.toFloat(),tween(motion))}
    val indicator:()->Float={selection ?: settled.value}
    val dragScale by animateFloatAsState(if (mode == 2 && motion > 0) 1.025f else 1f, tween(if (motion > 0) 120 else 0), label = "nav_lift")
    val shape = RoundedCornerShape(if (vertical) 32.dp else 100.dp)
    val entries = listOf("专注" to "focus", "统计" to "stats", "设置" to "settings")
    val fallbackContent=android.os.Build.VERSION.SDK_INT<33 || !LocalView.current.isHardwareAccelerated
    val readable=glassReadable()
    SideEffect {GlassDiagnostics.navCompositions++}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver {_,event -> if(event==Lifecycle.Event.ON_STOP) {shift=Offset.Zero;selection=null;mode=0;touch.reset()}}
        lifecycle.addObserver(observer);onDispose {lifecycle.removeObserver(observer)}
    }
    val dragModifier = Modifier.pointerInput(vertical) {
        detectDragGestures(onDragStart = { start ->
            if (mode != 2) { mode = 1; selection = settled.value;scope.launch {settled.stop()} }
        }, onDrag = { change, amount ->
            if (mode == 1) {
                change.consume()
                val delta = if (vertical) amount.y else amount.x
                selection = ((selection ?: pageLatest.toFloat()) + delta / (extent / 3)).coerceIn(0f, 2f)
            }
        }, onDragEnd = {
            if (mode == 1) {
                val end=selection ?: pageLatest.toFloat();val target=end.roundToInt().coerceIn(0,2);mode=0
                onPageLatest(target)
                scope.launch {settled.snapTo(end);selection=null;settled.animateTo(target.toFloat(),tween(motion))}
            }
        }, onDragCancel = { if (mode == 1) { selection = null; mode = 0;scope.launch {settled.animateTo(pageLatest.toFloat(),tween(motion))} } })
    }.pointerInput(vertical) {
        detectDragGesturesAfterLongPress(onDragStart = { mode = 2; selection = null; shift = Offset.Zero },
            onDrag = { change, amount -> change.consume(); shift = boundedDrag(shift + amount) },
            onDragEnd = { if (mode == 2) { onDockLatest(shift); shift = Offset.Zero; mode = 0 } },
            onDragCancel = { if (mode == 2) { shift = Offset.Zero; mode = 0 } })
    }
    BoxWithConstraints(modifier
        .then(if (vertical) Modifier.width(88.dp).heightIn(max = 360.dp).fillMaxHeight(0.62f) else Modifier.widthIn(max = 440.dp).fillMaxWidth().height(thickness))
        .onSizeChanged { measured = it }.glassTouch(touch).then(dragModifier).graphicsLayer {
            translationX=shift.x;translationY=shift.y
            scaleX = dragScale; scaleY = dragScale
            shadowElevation = 10.dp.toPx(); this.shape = shape; clip = false
        }.glassSurface(shape, GlassRole.CONTROL, navigation = true, interaction = touch,sampleMotion={shift}).padding(6.dp)
        .semantics { customActions = positionActions }.testTag("glass_navigation")) {
        val dimension = if (vertical) maxHeight else maxWidth
        SideEffect { extent = with(density) { dimension.toPx() } }
        val highlight = Tomato.copy(alpha = 0.10f)
        Box(Modifier.then(if (vertical) Modifier.fillMaxWidth().height(dimension / 3) else Modifier.fillMaxHeight().width(dimension / 3))
            .graphicsLayer {if(vertical) translationY=dimension.toPx()/3*indicator() else translationX=dimension.toPx()/3*indicator()}
            .glassDeform(touch).glassSurface(shape, GlassRole.CONTROL, navigation = true, interaction = touch,
                sampleMotion={Offset(indicator(),shift.x+shift.y)}).background(highlight, shape))
        val item: @Composable (Int, Modifier) -> Unit = { i, itemModifier ->
            Column(itemModifier.heightIn(min = 48.dp).selectable(page == i, role = Role.Tab, onClick = { onPage(i) })
                .graphicsLayer {
                    val influence=(1f-kotlin.math.abs(indicator()-i)).coerceIn(0f,1f)
                    val zoom=if(fallbackContent && motion>0 && !readable) 1f+.13f*influence else 1f
                    scaleX=zoom;scaleY=zoom
                }
                .testTag("nav_$i").padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)) {
                Glyph(entries[i].second, Modifier.size(24.dp), Ink)
                Text(entries[i].first, style = MaterialTheme.typography.titleSmall, color = Ink)
            }
        }
        if (vertical) Column(Modifier.fillMaxSize().navigationContentLens(indicator,true,touch)) { entries.indices.forEach { item(it, Modifier.weight(1f).fillMaxWidth()) } }
        else Row(Modifier.fillMaxSize().navigationContentLens(indicator,false,touch)) { entries.indices.forEach { item(it, Modifier.weight(1f).fillMaxHeight()) } }
    }
}
