package app.yike.focus.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

enum class Phase(val title: String) { FOCUS("专注"), SHORT_BREAK("短休息"), LONG_BREAK("长休息") }
enum class RunStatus { READY, RUNNING, PAUSED }
enum class Ambient(val title: String) { OFF("关闭"), RAIN("轻雨"), STREAM("溪流"), WAVES("缓浪"), FIRE("炉火"), WHITE("柔和白噪音"), BROWN("柔和棕噪音") }
enum class ThemeStyle(val title: String) { TOMATO("奶油番茄"), MATCHA("抹茶"), BLUE("雾蓝"), LILAC("丁香") }
enum class ThemeMode(val title: String) { LIGHT("浅色"), DARK("深色"), SYSTEM("跟随系统") }
enum class UiPreset(val title: String, val description: String) {
    CLASSIC("经典卡片", "圆环与卡片，熟悉的温暖节奏"),
    MINIMAL("极简数字", "留白与大数字，让注意力回到当下"),
    FLIP("翻页时钟", "机械翻页面板，有条理的专注工作台"),
    JOURNAL("手帐纸感", "日期标签与便笺，写下每一天的积累"),
}
enum class TimerFace(val title: String) { FOLLOW_PRESET("跟随预设"), RING("圆环"), DIGITAL("大数字"), FLIP("翻页") }
enum class UiDensity(val title: String) { COMFORTABLE("舒适"), COMPACT("紧凑") }
enum class InterfaceMaterial(val title: String) { TRADITIONAL("传统"), GLASS("液态玻璃") }
enum class NavDock(val title: String) { BOTTOM("底部"), TOP("顶部"), LEFT("左侧"), RIGHT("右侧") }

inline fun <reified T : Enum<T>> enumOrDefault(value: String?, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == value } ?: fallback

data class FocusSettings(
    val focusMinutes: Int = 25,
    val shortBreakMinutes: Int = 5,
    val longBreakMinutes: Int = 15,
    val longBreakEvery: Int = 4,
    val dailyGoal: Int = 8,
    val sound: Boolean = true,
    val vibration: Boolean = false,
    val keepAwake: Boolean = false,
    val ambient: Ambient = Ambient.OFF,
    val ambientVolume: Float = 0.18f,
    val themeStyle: ThemeStyle = ThemeStyle.TOMATO,
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val backgroundFile: String = "",
    val backgroundShade: Float = 0.65f,
    val uiPreset: UiPreset = UiPreset.CLASSIC,
    val timerFace: TimerFace = TimerFace.FOLLOW_PRESET,
    val uiDensity: UiDensity = UiDensity.COMFORTABLE,
    val decorativeMotion: Boolean = true,
    val interfaceMaterial: InterfaceMaterial = InterfaceMaterial.TRADITIONAL,
    val glassTransparency: Float = 0.5f,
    val glassBlur: Float = 0.6f,
    val glassReadable: Boolean = false,
) {
    fun resolvedTimerFace(): TimerFace = if (timerFace != TimerFace.FOLLOW_PRESET) timerFace else when (uiPreset) {
        UiPreset.CLASSIC -> TimerFace.RING
        UiPreset.FLIP -> TimerFace.FLIP
        else -> TimerFace.DIGITAL
    }
    fun resetAppearanceDefaults() = copy(timerFace = TimerFace.FOLLOW_PRESET,
        uiDensity = UiDensity.COMFORTABLE, decorativeMotion = true)

    fun duration(phase: Phase): Long = when (phase) {
        Phase.FOCUS -> focusMinutes
        Phase.SHORT_BREAK -> shortBreakMinutes
        Phase.LONG_BREAK -> longBreakMinutes
    }.coerceIn(1, 180) * 60_000L
}

data class ClockPoint(val wallMs: Long, val elapsedMs: Long, val bootCount: Int = 0)
data class FocusSpan(val startMs: Long, val endMs: Long) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0)
}

data class TimerState(
    val phase: Phase = Phase.FOCUS,
    val status: RunStatus = RunStatus.READY,
    val durationMs: Long = 25 * 60_000L,
    val remainingMs: Long = durationMs,
    val deadlineElapsedMs: Long = 0,
    val deadlineWallMs: Long = 0,
    val segmentStartWallMs: Long = 0,
    val startedAtMs: Long = 0,
    val sessionId: String = "",
    val taskId: String? = null,
    val taskTitle: String = "自由专注",
    val cycleCompleted: Int = 0,
    val spans: List<FocusSpan> = emptyList(),
    val bootCount: Int = 0,
    val checkpointElapsedMs: Long = 0,
) {
    fun remaining(nowElapsedMs: Long): Long = if (status == RunStatus.RUNNING) {
        (deadlineElapsedMs - nowElapsedMs).coerceIn(0, remainingMs)
    } else remainingMs

    fun currentSpans(nowElapsedMs: Long): List<FocusSpan> {
        if (phase != Phase.FOCUS || status != RunStatus.RUNNING) return spans
        val used = remainingMs - remaining(nowElapsedMs)
        return if (used > 0) spans + FocusSpan(segmentStartWallMs, segmentStartWallMs + used) else spans
    }
}

data class FocusRecord(
    val id: String,
    val taskId: String?,
    val taskTitle: String,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val focusedMs: Long,
    val completed: Boolean,
    val spans: List<FocusSpan>,
)

data class AdvanceResult(
    val state: TimerState,
    val records: List<FocusRecord> = emptyList(),
    val completedPhases: List<Phase> = emptyList(),
)

/** Pure state machine. Wall time labels records; monotonic time drives all countdowns. */
object TimerEngine {
    fun ready(settings: FocusSettings, cycle: Int = 0): TimerState = TimerState(
        durationMs = settings.duration(Phase.FOCUS),
        remainingMs = settings.duration(Phase.FOCUS),
        cycleCompleted = cycle,
    )

    fun start(
        state: TimerState, settings: FocusSettings, clock: ClockPoint,
        taskId: String? = null, taskTitle: String = "自由专注",
    ): TimerState {
        if (state.status == RunStatus.RUNNING) return state
        val base = if (state.status == RunStatus.READY) ready(settings, state.cycleCompleted).copy(
            sessionId = UUID.randomUUID().toString(),
            startedAtMs = clock.wallMs,
            taskId = taskId,
            taskTitle = taskTitle,
        ) else state
        return base.copy(
            status = RunStatus.RUNNING,
            deadlineElapsedMs = clock.elapsedMs + base.remainingMs,
            deadlineWallMs = clock.wallMs + base.remainingMs,
            segmentStartWallMs = clock.wallMs,
            bootCount = clock.bootCount,
            checkpointElapsedMs = clock.elapsedMs,
        )
    }

    fun pause(state: TimerState, clock: ClockPoint): TimerState {
        if (state.status != RunStatus.RUNNING) return state
        return state.copy(
            status = RunStatus.PAUSED,
            remainingMs = state.remaining(clock.elapsedMs),
            spans = state.currentSpans(clock.elapsedMs),
            deadlineElapsedMs = 0,
            deadlineWallMs = 0,
            checkpointElapsedMs = clock.elapsedMs,
        )
    }

    fun recoverAfterReboot(state: TimerState, clock: ClockPoint): TimerState {
        if (state.status != RunStatus.RUNNING || state.bootCount == clock.bootCount) return state
        val savedElapsed = state.checkpointElapsedMs.coerceIn(
            state.deadlineElapsedMs - state.remainingMs, state.deadlineElapsedMs,
        )
        return pause(state, clock.copy(elapsedMs = savedElapsed)).copy(bootCount = clock.bootCount)
    }

    fun finishEarly(state: TimerState, settings: FocusSettings, clock: ClockPoint): AdvanceResult {
        if (state.status == RunStatus.READY) return AdvanceResult(state)
        val paused = pause(state, clock)
        val record = if (state.phase == Phase.FOCUS) record(paused, false) else null
        val cycle = if (state.phase == Phase.LONG_BREAK) 0 else state.cycleCompleted
        return AdvanceResult(ready(settings, cycle), listOfNotNull(record))
    }

    fun advance(state: TimerState, settings: FocusSettings, clock: ClockPoint): AdvanceResult {
        var next = state
        val records = mutableListOf<FocusRecord>()
        val phases = mutableListOf<Phase>()
        // At most focus + rest can elapse: the next focus always waits for a user action.
        repeat(2) {
            if (next.status != RunStatus.RUNNING || clock.elapsedMs < next.deadlineElapsedMs) {
                return AdvanceResult(next, records, phases)
            }
            phases += next.phase
            if (next.phase == Phase.FOCUS) {
                record(pause(next, clock.copy(elapsedMs = next.deadlineElapsedMs)), true)?.let(records::add)
                val cycle = next.cycleCompleted + 1
                val breakPhase = if (cycle >= settings.longBreakEvery.coerceAtLeast(1)) Phase.LONG_BREAK else Phase.SHORT_BREAK
                val duration = settings.duration(breakPhase)
                next = TimerState(
                    phase = breakPhase,
                    status = RunStatus.RUNNING,
                    durationMs = duration,
                    remainingMs = duration,
                    deadlineElapsedMs = next.deadlineElapsedMs + duration,
                    deadlineWallMs = next.deadlineWallMs + duration,
                    segmentStartWallMs = next.deadlineWallMs,
                    startedAtMs = next.deadlineWallMs,
                    cycleCompleted = cycle,
                    bootCount = clock.bootCount,
                    checkpointElapsedMs = minOf(clock.elapsedMs, next.deadlineElapsedMs + duration),
                )
            } else {
                next = ready(settings, if (next.phase == Phase.LONG_BREAK) 0 else next.cycleCompleted)
            }
        }
        return AdvanceResult(next, records, phases)
    }

    private fun record(state: TimerState, completed: Boolean): FocusRecord? {
        val total = state.spans.sumOf(FocusSpan::durationMs)
        if (total <= 0 || state.sessionId.isBlank()) return null
        return FocusRecord(
            state.sessionId, state.taskId, state.taskTitle, state.startedAtMs,
            state.spans.last().endMs, total, completed, state.spans,
        )
    }
}

data class DayStats(val date: LocalDate, val focusMs: Long, val completedRounds: Int)

object Statistics {
    fun forDay(records: List<FocusRecord>, date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): DayStats {
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val millis = records.sumOf { record ->
            record.spans.sumOf { span -> (minOf(span.endMs, end) - maxOf(span.startMs, start)).coerceAtLeast(0) }
        }
        val completed = records.count { it.completed && it.endedAtMs >= start && it.endedAtMs < end }
        return DayStats(date, millis, completed)
    }

    fun dateOf(wallMs: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(wallMs).atZone(zone).toLocalDate()
}
