package app.yike.focus.data

import app.yike.focus.domain.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class GlassGardenBackupTest {
    private val record = FocusRecord("round-one", null, "读书", 1000, 2000, 1000, true, listOf(FocusSpan(1000,2000)))
    private val data = BackupData(emptyList(), listOf(record), FocusSettings(interfaceMaterial = InterfaceMaterial.GLASS,
        glassTransparency = .85f, glassBlur = .3f, glassReadable = true), exportedAt = 2000, gardenUsed = setOf(record.id))
    @Test fun newOptionalFieldsAndGardenRoundTripWithoutDeviceDock() {
        val bytes = ByteArrayOutputStream().also { BackupCodec.write(it, data) }.toByteArray()
        assertEquals(data, BackupCodec.read(ByteArrayInputStream(bytes)))
        assertFalse(String(BackupCodec.encode(data)).contains("navigation_dock"))
    }
    @Test fun oldBackupHasNoGardenAndUsesTraditionalDefaults() {
        val j = JSONObject(String(BackupCodec.encode(data)))
        j.remove("garden")
        listOf("interfaceMaterial", "glassTransparency", "glassBlur", "glassReadable").forEach { j.getJSONObject("settings").remove(it) }
        val old = BackupCodec.decode(j.toString().toByteArray())
        assertNull(old.gardenUsed)
        assertEquals(InterfaceMaterial.TRADITIONAL, old.settings.interfaceMaterial)
        assertEquals(.5f, old.settings.glassTransparency)
    }
    @Test fun invalidGardenIdsOrUnfinishedRoundsRejectWholeFile() {
        reject(data.copy(gardenUsed = setOf("unknown")))
        reject(data.copy(records = listOf(record.copy(completed = false))))
        val j = JSONObject(String(BackupCodec.encode(data)))
        j.getJSONObject("garden").getJSONArray("usedRecordIds").put(record.id)
        try { BackupCodec.decode(j.toString().toByteArray()); fail("Must reject duplicates") } catch (_: IllegalArgumentException) { }
    }
    @Test fun outOfRangeGlassValuesRejectFileAndUnknownMaterialFallsBack() {
        reject(data.copy(settings = data.settings.copy(glassTransparency = 1.1f)))
        reject(data.copy(settings = data.settings.copy(glassBlur = -.1f)))
        val j = JSONObject(String(BackupCodec.encode(data)))
        j.getJSONObject("settings").put("interfaceMaterial", "FUTURE")
        assertEquals(InterfaceMaterial.TRADITIONAL, BackupCodec.decode(j.toString().toByteArray()).settings.interfaceMaterial)
    }
    private fun reject(value: BackupData) {
        try { BackupCodec.decode(BackupCodec.encode(value)); fail("Must reject invalid backup") } catch (_: IllegalArgumentException) { }
    }
}
