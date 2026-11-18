package app.yike.focus

import kotlinx.coroutines.launch
import app.yike.focus.data.FocusRepository
import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.graphics.luminance
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yike.focus.domain.*
import app.yike.focus.platform.TimerAction
import app.yike.focus.ui.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val app = application as FocusApplication
            val consent by app.repository.legalConsentVersion.collectAsStateWithLifecycle(initialValue = null)
            val scope = rememberCoroutineScope()
            var error by remember { mutableStateOf(false) }
            when {
                consent == null -> Unit
                consent!! < FocusRepository.LEGAL_VERSION -> FocusTheme {
                    LegalConsentScreen(onAgree = {
                        scope.launch {
                            try { app.repository.setLegalConsentVersion(FocusRepository.LEGAL_VERSION) }
                            catch (_: java.io.IOException) { error = true }
                        }
                    }, onExit = ::finishAndRemoveTask, saveError = error)
                }
                else -> {
                    val config by app.controller.settings.collectAsStateWithLifecycle()
                    FocusTheme(config) { AppContent() }
                }
            }
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun AppContent() {
        val app = application as FocusApplication
        val controller = app.controller
        val state by controller.state.collectAsStateWithLifecycle()
        val settings by controller.settings.collectAsStateWithLifecycle()
        val tasks by controller.tasks.collectAsStateWithLifecycle()
        val records by controller.records.collectAsStateWithLifecycle()
        val dock by app.repository.navDock.collectAsStateWithLifecycle(initialValue = NavDock.BOTTOM)
        val selectedTaskId by controller.selectedTaskId.collectAsStateWithLifecycle()
        val clock = controller.elapsed.collectAsStateWithLifecycle()
        // A day boundary can affect the UI while idle; sub-day ticks cannot.
        val today by remember { derivedStateOf { clock.value; Statistics.dateOf(System.currentTimeMillis()) } }
        val elapsed = if (state.status == RunStatus.RUNNING) clock.value else state.checkpointElapsedMs
        val loaded by controller.loaded.collectAsStateWithLifecycle()
        val notice by controller.notice.collectAsStateWithLifecycle()
        var fullscreen by rememberSaveable { mutableStateOf(false) }
        var explainNotifications by rememberSaveable { mutableStateOf(false) }
        val preview by app.soundPlayer.preview.collectAsStateWithLifecycle()
        LaunchedEffect(settings, state.phase, state.status) {
            app.soundPlayer.update(state.phase == Phase.FOCUS && state.status == RunStatus.RUNNING, settings)
        }
        val lifecycle = LocalLifecycleOwner.current
        val light = Paper.luminance() > 0.5f
        DisposableEffect(fullscreen, light) {
            val bars = WindowCompat.getInsetsController(window, window.decorView)
            bars.isAppearanceLightStatusBars = light; bars.isAppearanceLightNavigationBars = light
            bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (fullscreen) bars.hide(WindowInsetsCompat.Type.systemBars()) else bars.show(WindowInsetsCompat.Type.systemBars())
            onDispose { bars.show(WindowInsetsCompat.Type.systemBars()) }
        }
        DisposableEffect(lifecycle) {
            val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) app.soundPlayer.cancelPreview() }
            lifecycle.lifecycle.addObserver(observer)
            onDispose { lifecycle.lifecycle.removeObserver(observer); app.soundPlayer.cancelPreview() }
        }
        var page by rememberSaveable { mutableIntStateOf(0) }
        val snackbar = remember { SnackbarHostState() }
        val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
        val zone = java.time.ZoneId.systemDefault()
        val savedToday = remember(records, today, zone) { Statistics.forDay(records, today, zone) }
        val alreadySaved = remember(records, state.sessionId) { records.any { it.id == state.sessionId } }
        val liveRecord = if (!alreadySaved && state.phase == Phase.FOCUS) {
            val spans = state.currentSpans(elapsed)
            if (spans.isEmpty()) null else FocusRecord(state.sessionId, state.taskId, state.taskTitle, state.startedAtMs,
                spans.last().endMs, spans.sumOf { it.durationMs }, false, spans)
        } else null
        val todayStats = if (liveRecord == null) savedToday else savedToday.copy(
            focusMs = savedToday.focusMs + Statistics.forDay(listOf(liveRecord), today, zone).focusMs)

        LaunchedEffect(notice) {
            notice?.let { snackbar.showSnackbar(it); if (controller.notice.value == it) controller.notice.value = null }
        }
        DisposableEffect(settings.keepAwake, state.status, state.phase) {
            if (settings.keepAwake && state.status == RunStatus.RUNNING && state.phase == Phase.FOCUS) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        }
        fun start() {
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                val prompts = getSharedPreferences("permission_prompts", MODE_PRIVATE)
                if (!prompts.getBoolean("notifications_explained", false)) {
                    prompts.edit().putBoolean("notifications_explained", true).apply()
                    explainNotifications = true
                }
            }
            controller.submit(TimerAction.START)
        }

        FocusBackdropHost(settings, enabled = settings.interfaceMaterial == InterfaceMaterial.GLASS || fullscreen || page == 0) {
        if (explainNotifications) FocusAlertDialog(onDismissRequest = { explainNotifications = false },
            title = { Text("允许计时提醒？") }, text = { Text("通知用于显示倒计时、暂停操作和结束提醒。拒绝后仍可使用应用，之后可在设置中开启。") },
            confirmButton = { FocusTextButton(onClick = { explainNotifications = false; if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("允许提醒") } },
            dismissButton = { FocusTextButton(onClick = { explainNotifications = false }) { Text("暂不开启") } })
        FocusContentTransition(fullscreen) { expanded ->
        if (expanded) {
            FullscreenTimer(state, settings, elapsed,
                if (state.status == RunStatus.READY) tasks.find { it.id == selectedTaskId }?.title ?: "自由专注" else state.taskTitle,
                ::start, { controller.submit(TimerAction.PAUSE) }, { controller.submit(TimerAction.END) }, { fullscreen = false })
        } else {
        AppScaffold(settings, page, today, snackbar,
            onPage = { app.soundPlayer.cancelPreview(); page = it }, navigationDock = dock,
            onDock = { target -> app.applicationScope.launchSafe({ controller.notice.value = "导航位置未保存，请重试。" }) { app.repository.setNavDock(target) } }) {
            if (!loaded) Box(Modifier.fillMaxWidth().heightIn(min = 200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else {
                CompositionLocalProvider(LocalDeskRepo provides app.repository) { when (page) {
                    0 -> FocusScreen(
                        state, settings, elapsed, tasks, selectedTaskId, todayStats,
                        onFullscreen = { app.soundPlayer.cancelPreview(); fullscreen = true },
                        onPreview = { app.soundPlayer.update(state.phase == Phase.FOCUS && state.status == RunStatus.RUNNING, settings); app.soundPlayer.preview(settings.ambient) },
                        previewing = preview != Ambient.OFF,
                        onStart = ::start,
                        onPause = { controller.submit(TimerAction.PAUSE) },
                        onEnd = { controller.submit(TimerAction.END) },
                        onSettings = { page = 2 },
                        onSelectTask = { id -> app.applicationScope.launchSafe({ controller.notice.value = "任务选择未保存，请检查存储空间。" }) { app.repository.selectTask(id) } },
                        onSaveTask = { task -> app.applicationScope.launchSafe({ controller.notice.value = "任务未保存，请检查存储空间。" }) { app.repository.saveTask(task) } },
                        onDeleteTask = { task -> app.applicationScope.launchSafe({ controller.notice.value = "任务未删除，请重试。" }) { app.repository.deleteTask(task.id) } },
                        onAmbient = { ambient -> controller.updateSettings { it.copy(ambient = ambient) } },
                        onVolume = { volume -> controller.updateSettings { it.copy(ambientVolume = volume) } },
                    )
                    1 -> StatsScreen(records, liveRecord, today, todayStats, settings.dailyGoal)
                    else -> SettingsScreen(settings, controller::updateSettings)
                } }
            }
        }
        }
        }
        }
    }
}
