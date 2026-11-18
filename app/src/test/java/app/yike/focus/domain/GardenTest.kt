package app.yike.focus.domain

import org.junit.Assert.*
import org.junit.Test

class GardenTest {
    private fun record(id: String, completed: Boolean = true) = FocusRecord(id, null, "阅读", 1000, 2000, 1000,
        completed, listOf(FocusSpan(1000, 2000)))
    @Test fun onlyCompleteUniqueRecordsProduceWaterIncludingHistory() {
        val records = listOf(record("old"), record("old"), record("ended", false), record("new"))
        assertEquals(setOf("old", "new"), Garden.earned(records))
        assertEquals(setOf("new"), Garden.available(records, setOf("old")))
    }
    @Test fun fourWateringsHarvestAndNextPlantStartsAtSeed() {
        val records = (1..9).map { record("r$it") }
        for (count in 0..9) {
            val used = records.take(count).map { it.id }.toSet()
            assertEquals(count / 4, Garden.harvests(records, used))
            assertEquals(count % 4, Garden.stage(records, used))
        }
    }
    @Test fun unionOnTwoDevicesCannotDoubleSpendSameRecord() {
        val records = (1..8).map { record("r$it") }
        val local = setOf("r1", "r2", "r3")
        val other = setOf("r2", "r3", "r4")
        val once = local + other
        assertEquals(once, once + other)
        assertEquals(1, Garden.harvests(records, once))
        assertEquals(4, Garden.available(records, once).size)
    }
    @Test fun invalidOrConflictingIncompleteRecordCannotMintRewards() {
        val records = listOf(record("complete"), record("incomplete", false))
        assertEquals(setOf("complete"), Garden.validUsed(records, setOf("complete", "missing", "incomplete")))
    }
    @Test fun sevenTapsWithinFiveSecondsOpenAndResetGate() {
        val gate = SecretTapGate()
        repeat(6) { assertFalse(gate.tap(1000L + it * 500)) }
        assertTrue(gate.tap(4000)); assertFalse(gate.tap(4001))
    }
    @Test fun expiredOrNonMonotonicTapSequenceRestarts() {
        val gate = SecretTapGate()
        repeat(6) { gate.tap(1000L + it * 100) }
        assertFalse(gate.tap(7000))
        repeat(5) { assertFalse(gate.tap(7100L + it * 100)) }
        assertTrue(gate.tap(7700))
        gate.tap(9000); assertFalse(gate.tap(8000))
    }
}
