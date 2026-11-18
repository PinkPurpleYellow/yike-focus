package app.yike.focus.data

import app.yike.focus.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupTest {
    private val task = TaskEntity("task-one", "读书", 100, false, false, 200)
    private val record = FocusRecord("session-one", task.id, task.title, 1_000, 6_000, 3_000, false,
        listOf(FocusSpan(1_000, 2_000), FocusSpan(4_000, 6_000)))
    private val backup = BackupData(listOf(task), listOf(record), FocusSettings(themeStyle = ThemeStyle.LILAC, themeMode = ThemeMode.DARK), exportedAt = 1000)

    @Test fun zipRoundTripPreservesRecordsTasksAndSettings() {
        val output = ByteArrayOutputStream(); BackupCodec.write(output, backup)
        val result = BackupCodec.read(ByteArrayInputStream(output.toByteArray()))
        assertEquals(backup, result)
        assertFalse(String(BackupCodec.encode(backup)).contains("timer_snapshot"))
    }
    @Test fun importingTwiceDoesNotAddStatistics() {
        val first = BackupMerge.plan(emptyList(), emptyList(), backup)
        val second = BackupMerge.plan(first.tasks, first.records, backup)
        assertTrue(second.tasks.isEmpty()); assertTrue(second.records.isEmpty()); assertEquals(2, second.duplicates)
    }
    @Test fun allInterfacePresetsRoundTripWithTheirOverrides() {
        UiPreset.entries.forEach { preset ->
            val data = backup.copy(settings = backup.settings.copy(uiPreset = preset, timerFace = TimerFace.FLIP,
                uiDensity = UiDensity.COMPACT, decorativeMotion = false))
            val output = ByteArrayOutputStream(); BackupCodec.write(output, data)
            assertEquals(data, BackupCodec.read(ByteArrayInputStream(output.toByteArray())))
        }
    }
    @Test fun originalBackupWithoutAppearanceFieldsUsesClassicAndKeepsExistingColors() {
        val j = JSONObject(String(BackupCodec.encode(backup)))
        val settings = j.getJSONObject("settings")
        listOf("uiPreset", "timerFace", "uiDensity", "decorativeMotion").forEach { settings.remove(it) }
        val decoded = BackupCodec.decode(j.toString().toByteArray())
        assertEquals(backup, decoded)
        assertEquals(UiPreset.CLASSIC, decoded.settings.uiPreset)
    }
    @Test fun unknownFutureAppearanceValuesDoNotBlockValidRecords() {
        val j = JSONObject(String(BackupCodec.encode(backup)))
        val settings = j.getJSONObject("settings")
        listOf("uiPreset", "timerFace", "uiDensity", "theme", "mode").forEach { settings.put(it, "FUTURE_VALUE") }
        val decoded = BackupCodec.decode(j.toString().toByteArray())
        assertEquals(backup.records, decoded.records)
        assertEquals(backup.tasks, decoded.tasks)
        assertEquals(UiPreset.CLASSIC, decoded.settings.uiPreset)
        assertEquals(TimerFace.FOLLOW_PRESET, decoded.settings.timerFace)
        assertEquals(ThemeStyle.TOMATO, decoded.settings.themeStyle)
    }
    @Test fun deletionFromNewerDeviceWinsAndStaleBackupCannotResurrect() {
        val tombstone = task.copy(deleted = true, updatedAt = 300)
        val imported = BackupMerge.plan(listOf(task), emptyList(), backup.copy(tasks = listOf(tombstone)))
        assertEquals(listOf(tombstone), imported.tasks)
        val stale = BackupMerge.plan(listOf(tombstone), emptyList(), backup)
        assertTrue(stale.tasks.isEmpty())
    }
    @Test fun equalTaskTimestampAndConflictingRecordKeepLocalData() {
        val changed = backup.copy(tasks = listOf(task.copy(title = "另一个名称")), records = listOf(record.copy(taskTitle = "另一个名称")))
        val result = BackupMerge.plan(listOf(task), listOf(record), changed)
        assertTrue(result.tasks.isEmpty()); assertTrue(result.records.isEmpty()); assertEquals(2, result.conflicts)
    }
    @Test fun independentDeviceRecordsMergeWithoutDeduplicatingByTime() {
        val other = record.copy(id = "session-two")
        val result = BackupMerge.plan(emptyList(), listOf(record), backup.copy(records = listOf(record, other)))
        assertEquals(listOf(other), result.records); assertEquals(1, result.duplicates)
    }
    @Test fun corruptedOrFutureVersionIsRejected() {
        val j = JSONObject(String(BackupCodec.encode(backup))); j.put("formatVersion", 99)
        assertRejected { BackupCodec.decode(j.toString().toByteArray()) }
        assertRejected { BackupCodec.read(ByteArrayInputStream(byteArrayOf(1,2,3))) }
    }
    @Test fun invalidSpansAndIncorrectTotalsAreRejected() {
        assertRejected { BackupCodec.decode(BackupCodec.encode(backup.copy(records = listOf(record.copy(focusedMs = 99))))) }
        val invalid = record.copy(spans = listOf(FocusSpan(2000,1000)))
        assertRejected { BackupCodec.decode(BackupCodec.encode(backup.copy(records = listOf(invalid)))) }
        assertRejected { BackupCodec.decode(BackupCodec.encode(backup.copy(records = listOf(record, record)))) }
    }
    @Test fun archivePathsOutsideDeclaredEntriesAreRejected() {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { it.putNextEntry(ZipEntry("../background.jpg")); it.write(byteArrayOf(1)); it.closeEntry() }
        assertRejected { BackupCodec.read(ByteArrayInputStream(output.toByteArray())) }
    }
    @Test fun originalRecordsSurviveWallClockMovingBackwardsBetweenPauses() {
        val original = record.copy(startedAtMs = 10_000, endedAtMs = 5_000, focusedMs = 3000,
            spans = listOf(FocusSpan(10_000, 11_000), FocusSpan(3_000, 5_000)))
        val decoded = BackupCodec.decode(BackupCodec.encode(backup.copy(records = listOf(original))))
        assertEquals(original, decoded.records.single())
    }
    private fun assertRejected(block: () -> Unit) {
        try { block(); fail("Expected rejected backup") } catch (_: IllegalArgumentException) { }
    }
}
