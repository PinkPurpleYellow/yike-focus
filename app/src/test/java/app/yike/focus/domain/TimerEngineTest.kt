package app.yike.focus.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TimerEngineTest {
    private val settings = FocusSettings(focusMinutes = 1, shortBreakMinutes = 1, longBreakMinutes = 2)
    private val start = ClockPoint(1_700_000_000_000, 10_000, 4)
    private fun clock(after: Long) = start.copy(wallMs = start.wallMs + after, elapsedMs = start.elapsedMs + after)

    @Test fun pauseExcludesTimeAwayAndPreservesTask() {
        var state = TimerEngine.start(TimerEngine.ready(settings), settings, start, "task", "读书")
        state = TimerEngine.pause(state, clock(20_000))
        assertEquals(40_000L, state.remainingMs)
        state = TimerEngine.start(state, settings, clock(120_000))
        val result = TimerEngine.advance(state, settings, clock(160_000))
        assertEquals(60_000L, result.records.single().focusedMs)
        assertEquals("读书", result.records.single().taskTitle)
        assertEquals(2, result.records.single().spans.size)
        assertEquals(Phase.SHORT_BREAK, result.state.phase)
    }

    @Test fun delayedAlarmFinishesRestButNeverStartsFocus() {
        val state = TimerEngine.start(TimerEngine.ready(settings), settings, start)
        val result = TimerEngine.advance(state, settings, clock(500_000))
        assertEquals(1, result.records.size)
        assertTrue(result.records.single().completed)
        assertEquals(RunStatus.READY, result.state.status)
        assertEquals(2, result.completedPhases.size)
        assertTrue(TimerEngine.advance(result.state, settings, clock(600_000)).records.isEmpty())
    }

    @Test fun fourthRoundStartsLongRestAndSkippingResetsCycle() {
        val state = TimerEngine.start(TimerEngine.ready(settings, 3), settings, start)
        val result = TimerEngine.advance(state, settings, clock(60_000))
        assertEquals(Phase.LONG_BREAK, result.state.phase)
        assertEquals(120_000L, result.state.remaining(clock(60_000).elapsedMs))
        val skip = TimerEngine.finishEarly(result.state, settings, clock(65_000))
        assertEquals(0, skip.state.cycleCompleted)
        assertTrue(skip.records.isEmpty())
    }

    @Test fun endingEarlyKeepsPartialTimeWithoutCompletedRound() {
        val state = TimerEngine.start(TimerEngine.ready(settings), settings, start)
        val result = TimerEngine.finishEarly(state, settings, clock(23_000))
        assertEquals(23_000L, result.records.single().focusedMs)
        assertFalse(result.records.single().completed)
        assertEquals(0, result.state.cycleCompleted)
    }

    @Test fun wallClockChangeCannotChangeCountdown() {
        val state = TimerEngine.start(TimerEngine.ready(settings), settings, start)
        val jumped = ClockPoint(start.wallMs + 86_400_000, start.elapsedMs + 15_000, 4)
        assertEquals(45_000L, state.remaining(jumped.elapsedMs))
        assertTrue(TimerEngine.advance(state, settings, jumped).records.isEmpty())
    }

    @Test fun rebootRestoresCheckpointPausedWithoutCountingPowerOff() {
        val state = TimerEngine.start(TimerEngine.ready(settings), settings, start)
            .copy(checkpointElapsedMs = start.elapsedMs + 15_000)
        val restored = TimerEngine.recoverAfterReboot(state, ClockPoint(start.wallMs + 1_000_000, 2_000, 5))
        assertEquals(RunStatus.PAUSED, restored.status)
        assertEquals(45_000L, restored.remainingMs)
        assertEquals(15_000L, restored.spans.sumOf { it.durationMs })
    }

    @Test fun activeDurationKeepsOldSettingsButNextRestUsesNewSettings() {
        val state = TimerEngine.start(TimerEngine.ready(settings), settings, start)
        val changed = settings.copy(focusMinutes = 50, shortBreakMinutes = 3)
        assertEquals(60_000L, state.durationMs)
        val result = TimerEngine.advance(state, changed, clock(60_000))
        assertEquals(180_000L, result.state.durationMs)
        val ready = TimerEngine.finishEarly(result.state, changed, clock(61_000)).state
        assertEquals(3_000_000L, ready.durationMs)
    }

    @Test fun midnightSplitsMinutesButAttributesCompletionToEndDay() {
        val zone = ZoneId.of("Asia/Shanghai")
        val day = LocalDate.of(2026, 11, 8)
        val midnight = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val span = FocusSpan(midnight - 300_000, midnight + 300_000)
        val record = FocusRecord("id", null, "自由专注", span.startMs, span.endMs, 600_000, true, listOf(span))
        assertEquals(300_000L, Statistics.forDay(listOf(record), day, zone).focusMs)
        assertEquals(0, Statistics.forDay(listOf(record), day, zone).completedRounds)
        assertEquals(300_000L, Statistics.forDay(listOf(record), day.plusDays(1), zone).focusMs)
        assertEquals(1, Statistics.forDay(listOf(record), day.plusDays(1), zone).completedRounds)
    }
}
