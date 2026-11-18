package app.yike.focus.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import android.view.accessibility.AccessibilityManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yike.focus.domain.*
import kotlinx.coroutines.delay

internal val FullscreenControlsVisibleKey = SemanticsPropertyKey<Boolean>("FullscreenControlsVisible")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FullscreenTimer(state: TimerState, settings: FocusSettings, elapsed: Long, taskTitle: String,
    onStart: () -> Unit, onPause: () -> Unit, onEnd: () -> Unit, onExit: () -> Unit) {
    var visible by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var confirmEnd by remember { mutableStateOf(false) }
    var showTask by remember { mutableStateOf(false) }
    val androidAccessibility = LocalContext.current.getSystemService(AccessibilityManager::class.java)
    var touchExploration by remember { mutableStateOf(androidAccessibility?.isTouchExplorationEnabled == true) }
    DisposableEffect(androidAccessibility) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { touchExploration = it }
        androidAccessibility?.addTouchExplorationStateChangeListener(listener)
        onDispose { androidAccessibility?.removeTouchExplorationStateChangeListener(listener) }
    }
    val timeout = LocalAccessibilityManager.current?.calculateRecommendedTimeoutMillis(5_000,
        containsIcons = true, containsText = true, containsControls = true) ?: 5_000L
    BackHandler(onBack = onExit)
    LaunchedEffect(interaction, state.status, touchExploration, timeout) {
        visible = true
        if (state.status == RunStatus.RUNNING && !touchExploration) { delay(timeout); visible = false }
    }
    val opacity by animateFloatAsState(if (visible) 1f else 0f, tween(motionMillis(200)), label = "fullscreen_controls")
    FocusBackdropHost(settings) {
    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).clickable { visible = true; interaction++ }) {
        val compact = maxHeight < 480.dp
        val displayHeight = (maxHeight * if (compact) 0.3f else 0.46f).coerceAtLeast(70.dp)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(if (compact) 16.dp else 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Column(Modifier.widthIn(max = 1100.dp).fillMaxWidth().testTag("fullscreen_content"), horizontalAlignment = Alignment.CenterHorizontally) {
            WallpaperContent {
            Text(state.phase.title + if (state.status == RunStatus.PAUSED) " · 已暂停" else "", color = Ink, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(if (compact) 8.dp else 16.dp))
            TimerDisplay(state.remaining(elapsed), state.durationMs, state.phase, state.status, settings,
                large = true, heightLimit = displayHeight)
            Text(if (state.phase == Phase.FOCUS) taskTitle else "休息一下，下一轮由你开启", Modifier.clickable { showTask = true }.heightIn(min = 48.dp).wrapContentHeight(Alignment.CenterVertically), textAlign = TextAlign.Center,
                maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis, color = Muted, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(if (compact) 8.dp else 24.dp))
            // Keep the entire measured controls, including wrapped rows, in layout while invisible.
            Box(Modifier.fillMaxWidth().testTag("fullscreen_controls").graphicsLayer { alpha = opacity }
                .then(if (visible) Modifier.semantics { this[FullscreenControlsVisibleKey] = true }
                else Modifier.clearAndSetSemantics { this[FullscreenControlsVisibleKey] = false }), contentAlignment = Alignment.Center) {
                    WallpaperContent {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FocusButton(enabled = visible, onClick = { interaction++; if (state.status == RunStatus.RUNNING) onPause() else onStart() }, modifier = Modifier.testTag("fullscreen_primary")) {
                            Text(when (state.status) { RunStatus.READY -> "开始专注"; RunStatus.PAUSED -> "继续"; else -> "暂停" }) }
                        if (state.status != RunStatus.READY) FocusOutlinedButton(enabled = visible, onClick = {
                            interaction++; if (state.phase == Phase.FOCUS) confirmEnd = true else onEnd()
                        }) { Text(if (state.phase == Phase.FOCUS) "结束" else "跳过休息", color = Ink) }
                        FocusTextButton(enabled = visible, onClick = onExit, modifier = Modifier.testTag("exit_fullscreen")) { Text("退出全屏", color = Ink) }
                    }
                    }
            }
            }
        }
    }
    }
    if (showTask) FocusAlertDialog(onDismissRequest = { showTask = false }, title = { Text("当前任务") },
        text = { Text(taskTitle, Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { FocusTextButton(onClick = { showTask = false }) { Text("关闭") } })
    if (confirmEnd) FocusAlertDialog(onDismissRequest = { confirmEnd = false }, title = { Text("结束这次专注？") },
        text = { Text("保留实际专注时间，本次不计为完整一轮。") },
        confirmButton = { FocusTextButton(onClick = { confirmEnd = false; onEnd() }) { Text("结束并保存") } },
        dismissButton = { FocusTextButton(onClick = { confirmEnd = false }) { Text("继续专注") } })
}
