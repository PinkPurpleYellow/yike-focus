package app.yike.focus.platform

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import app.yike.focus.data.FocusRepository
import app.yike.focus.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class TimerAction { START, PAUSE, END, ALARM }

class FocusController(
    private val context: Context,
    val repository: FocusRepository,
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private val initialized = CompletableDeferred<Unit>()
    private val mutableState = MutableStateFlow(TimerState())
    val state = mutableState.asStateFlow()
    val settings = repository.settings.stateIn(scope, SharingStarted.Eagerly, FocusSettings())
    val tasks = repository.tasks.stateIn(scope, SharingStarted.Eagerly, emptyList())
    val records = repository.records.stateIn(scope, SharingStarted.Eagerly, emptyList())
    val selectedTaskId = repository.selectedTaskId.stateIn(scope, SharingStarted.Eagerly, null)
    val elapsed = MutableStateFlow(SystemClock.elapsedRealtime())
    val loaded = MutableStateFlow(false)
    val notice = MutableStateFlow<String?>(null)

    init {
        scope.launch {
            try {
                val config = repository.currentSettings()
                val saved = repository.readSnapshot() ?: TimerEngine.ready(config)
                val restored = TimerEngine.recoverAfterReboot(saved, clock())
                if (restored != saved) notice.value = "设备已重启，计时已恢复为暂停，点击继续即可。"
                mutableState.value = restored
                if (restored != saved) repository.saveSnapshot(restored)
                synchronizeLocked(config)
                TimerPlatform.refresh(context, state.value, config)
            } catch (error: Exception) {
                notice.value = "恢复计时失败：${error.localizedMessage ?: "请重新打开应用"}"
            } finally {
                loaded.value = true
                initialized.complete(Unit)
            }
            while (isActive) {
                elapsed.value = SystemClock.elapsedRealtime()
                if (state.value.status == RunStatus.RUNNING) {
                    runCatching {
                        mutex.withLock {
                            synchronizeLocked(settings.value)
                            val current = state.value
                            if (current.status == RunStatus.RUNNING && elapsed.value - current.checkpointElapsedMs >= 15_000) {
                                val checkpoint = current.copy(checkpointElapsedMs = elapsed.value)
                                repository.saveSnapshot(checkpoint)
                                mutableState.value = checkpoint
                            }
                        }
                    }.onFailure { notice.value = "暂时无法保存计时，请检查存储空间。" }
                }
                val remaining = state.value.remaining(SystemClock.elapsedRealtime())
                val untilDisplayChange = remaining % 1_000L
                delay(if (state.value.status == RunStatus.RUNNING && untilDisplayChange > 0) untilDisplayChange else 1_000L)
            }
        }
        scope.launch {
            initialized.await()
            repository.settings.collect { config ->
                mutex.withLock {
                    if (state.value.status == RunStatus.READY) {
                        val next = TimerEngine.ready(config, state.value.cycleCompleted)
                        // Appearance/audio preferences do not change the ready timer.
                        if (next != state.value) {
                            repository.saveSnapshot(next)
                            mutableState.value = next
                        }
                    }
                }
            }
        }
    }

    fun submit(action: TimerAction) = scope.launch {
        runCatching { handle(action) }.onFailure { notice.value = "操作未完成：${it.localizedMessage ?: "请重试"}" }
    }

    suspend fun handle(action: TimerAction) {
        initialized.await()
        mutex.withLock {
            val config = repository.currentSettings()
            synchronizeLocked(config)
            val current = state.value
            val result = when (action) {
                TimerAction.START -> {
                    val task = repository.selectedTask()
                    AdvanceResult(TimerEngine.start(current, config, clock(), task?.id, task?.title ?: "自由专注"))
                }
                TimerAction.PAUSE -> AdvanceResult(TimerEngine.pause(current, clock()))
                TimerAction.END -> TimerEngine.finishEarly(current, config, clock())
                TimerAction.ALARM -> AdvanceResult(current)
            }
            apply(result, config)
        }
    }

    private suspend fun synchronizeLocked(config: FocusSettings) {
        val result = TimerEngine.advance(state.value, config, clock())
        if (result.state != state.value) apply(result, config)
    }

    private suspend fun apply(result: AdvanceResult, config: FocusSettings) {
        result.records.forEach { repository.saveRecord(it) }
        repository.saveSnapshot(result.state)
        mutableState.value = result.state
        elapsed.value = SystemClock.elapsedRealtime()
        TimerPlatform.refresh(context, result.state, config)
        result.completedPhases.lastOrNull()?.let { NotificationKit.finished(context, it, config) }
    }

    fun updateSettings(transform: (FocusSettings) -> FocusSettings) = scope.launch {
        runCatching { repository.updateSettings(transform) }
            .onFailure { notice.value = "设置未保存，请检查存储空间。" }
    }
    fun clock() = ClockPoint(
        System.currentTimeMillis(), SystemClock.elapsedRealtime(),
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0),
    )
}
