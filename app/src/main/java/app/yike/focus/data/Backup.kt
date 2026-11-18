package app.yike.focus.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import androidx.room.withTransaction
import app.yike.focus.BuildConfig
import app.yike.focus.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupData(val tasks: List<TaskEntity>, val records: List<FocusRecord>, val settings: FocusSettings,
    val background: ByteArray? = null, val exportedAt: Long = System.currentTimeMillis(), val gardenUsed: Set<String>? = null)
data class MergePlan(val tasks: List<TaskEntity>, val records: List<FocusRecord>, val duplicates: Int, val conflicts: Int)
data class ImportResult(val tasks: Int, val records: Int, val duplicates: Int, val conflicts: Int, val settingsRestored: Boolean?, val settingsError: Boolean, val gardenRestored: Boolean? = null)

object BackupMerge {
    fun plan(tasks: List<TaskEntity>, records: List<FocusRecord>, incoming: BackupData): MergePlan {
        val oldTasks = tasks.associateBy { it.id }; val oldRecords = records.associateBy { it.id }
        val saveTasks = mutableListOf<TaskEntity>(); val saveRecords = mutableListOf<FocusRecord>()
        var duplicates = 0; var conflicts = 0
        incoming.tasks.forEach { item ->
            val old = oldTasks[item.id]
            when { old == null || item.updatedAt > old.updatedAt -> saveTasks += item
                old == item -> duplicates++
                else -> conflicts++ }
        }
        incoming.records.forEach { item ->
            val old = oldRecords[item.id]
            when { old == null -> saveRecords += item; old == item -> duplicates++; else -> conflicts++ }
        }
        return MergePlan(saveTasks, saveRecords, duplicates, conflicts)
    }
}

object BackupCodec {
    const val MAX_FILE = 50 * 1024 * 1024
    fun encode(data: BackupData): ByteArray {
        val s = data.settings
        return JSONObject().apply {
            put("formatVersion", 1); put("applicationId", "app.yike.focus"); put("appVersion", BuildConfig.VERSION_NAME)
            put("exportedAt", data.exportedAt)
            data.gardenUsed?.let { put("garden", JSONObject().put("usedRecordIds", JSONArray(it.sorted()))) }
            put("tasks", JSONArray().apply { data.tasks.forEach { t -> put(JSONObject().apply {
                put("id", t.id); put("title", t.title); put("createdAt", t.createdAt); put("updatedAt", t.updatedAt)
                put("completed", t.completed); put("deleted", t.deleted)
            }) } })
            put("records", JSONArray().apply { data.records.forEach { r -> put(JSONObject().apply {
                put("id", r.id); put("taskId", r.taskId ?: JSONObject.NULL); put("taskTitle", r.taskTitle)
                put("startedAtMs", r.startedAtMs); put("endedAtMs", r.endedAtMs); put("focusedMs", r.focusedMs)
                put("completed", r.completed); put("spans", JsonCodec.spans(r.spans))
            }) } })
            put("settings", JSONObject().apply {
                put("focus", s.focusMinutes); put("short", s.shortBreakMinutes); put("long", s.longBreakMinutes)
                put("interval", s.longBreakEvery); put("goal", s.dailyGoal); put("sound", s.sound)
                put("vibration", s.vibration); put("awake", s.keepAwake); put("ambient", s.ambient.name)
                put("volume", s.ambientVolume.toDouble()); put("theme", s.themeStyle.name); put("mode", s.themeMode.name)
                put("shade", s.backgroundShade.toDouble())
                put("uiPreset", s.uiPreset.name); put("timerFace", s.timerFace.name)
                put("uiDensity", s.uiDensity.name); put("decorativeMotion", s.decorativeMotion)
                put("interfaceMaterial", s.interfaceMaterial.name); put("glassTransparency", s.glassTransparency.toDouble())
                put("glassBlur", s.glassBlur.toDouble()); put("glassReadable", s.glassReadable)
            })
        }.toString().toByteArray(Charsets.UTF_8)
    }

    fun decode(bytes: ByteArray, image: ByteArray? = null): BackupData {
        val j = JSONObject(bytes.toString(Charsets.UTF_8))
        require(j.getInt("formatVersion") == 1) { "不支持此备份版本，请使用兼容的新版应用。" }
        require(j.getString("applicationId") == "app.yike.focus") { "这不是一刻专注的备份。" }
        fun id(value: String): String { require(value.length in 1..128 && value.none { it.isISOControl() }); return value }
        fun title(value: String): String { require(value.isNotBlank() && value.length <= 120); return value }
        val tj = j.getJSONArray("tasks"); val rj = j.getJSONArray("records")
        require(tj.length() <= 100_000 && rj.length() <= 100_000) { "备份包含的项目过多。" }
        val tasks = (0 until tj.length()).map { n -> val t = tj.getJSONObject(n)
            TaskEntity(id(t.getString("id")), title(t.getString("title")), t.getLong("createdAt"),
                t.getBoolean("completed"), t.getBoolean("deleted"), t.getLong("updatedAt")).also {
                require(it.createdAt >= 0 && it.updatedAt >= 0)
            }
        }
        val records = (0 until rj.length()).map { n -> val r = rj.getJSONObject(n)
            val spans = JsonCodec.readSpans(r.getJSONArray("spans"))
            val record = FocusRecord(id(r.getString("id")), if (r.isNull("taskId")) null else id(r.getString("taskId")),
                title(r.getString("taskTitle")), r.getLong("startedAtMs"), r.getLong("endedAtMs"),
                r.getLong("focusedMs"), r.getBoolean("completed"), spans)
            require(record.startedAtMs in 0..253402300799000L && record.endedAtMs in 0..253402300799000L && spans.size in 1..100_000)
            // Wall time may move backwards between pauses in valid v1 records.
            // Check each duration and the total; elapsed time drives the timer.
            spans.forEach { span ->
                require(span.startMs >= 0 && span.endMs > span.startMs && span.endMs <= 253402300799000L)
                require(span.endMs - span.startMs <= 180 * 60_000L)
            }
            require(record.focusedMs > 0 && record.focusedMs <= 180 * 60_000L && record.focusedMs == spans.sumOf { it.durationMs })
            record
        }
        require(tasks.map { it.id }.distinct().size == tasks.size && records.map { it.id }.distinct().size == records.size) { "备份内部存在重复项目。" }
        val s = j.getJSONObject("settings")
        val volume = s.getDouble("volume").toFloat(); val shade = s.getDouble("shade").toFloat()
        require(volume.isFinite() && volume in 0f..1f && shade.isFinite() && shade in 0.25f..0.95f)
        val transparency = s.optDouble("glassTransparency", 0.5).toFloat()
        val blur = s.optDouble("glassBlur", 0.6).toFloat()
        require(transparency.isFinite() && transparency in 0f..1f && blur.isFinite() && blur in 0f..1f)
        val garden = if (j.has("garden")) {
            val ids = j.getJSONObject("garden").getJSONArray("usedRecordIds")
            require(ids.length() <= 100_000) { "花园数据过多。" }
            val completedIds = Garden.earned(records)
            (0 until ids.length()).map { id(ids.getString(it)) }.toSet().also { used ->
                require(used.size == ids.length() && completedIds.containsAll(used)) { "花园水滴必须对应完整专注记录。" }
            }
        } else null
        val settings = FocusSettings(s.getInt("focus"), s.getInt("short"), s.getInt("long"), s.getInt("interval"), s.getInt("goal"),
            s.getBoolean("sound"), s.getBoolean("vibration"), s.getBoolean("awake"), Ambient.valueOf(s.getString("ambient")),
            volume, enumOrDefault(s.optString("theme"), ThemeStyle.TOMATO),
            enumOrDefault(s.optString("mode"), ThemeMode.LIGHT), "", shade,
            enumOrDefault(s.optString("uiPreset"), UiPreset.CLASSIC),
            enumOrDefault(s.optString("timerFace"), TimerFace.FOLLOW_PRESET),
            enumOrDefault(s.optString("uiDensity"), UiDensity.COMFORTABLE),
            s.optBoolean("decorativeMotion", true),
            enumOrDefault(s.optString("interfaceMaterial"), InterfaceMaterial.TRADITIONAL),
            transparency, blur, s.optBoolean("glassReadable", false))
        require(listOf(settings.focusMinutes, settings.shortBreakMinutes, settings.longBreakMinutes).all { it in 1..180 }
            && settings.longBreakEvery in 1..12 && settings.dailyGoal in 1..40)
        image?.let { ImageStore.validate(it) }
        return BackupData(tasks, records, settings, image, j.getLong("exportedAt"), garden)
    }

    fun write(output: OutputStream, data: BackupData) {
        val json = encode(data)
        require(json.size.toLong() + (data.background?.size ?: 0) <= MAX_FILE) { "备份内容超过 50 MB。" }
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("backup.json")); zip.write(json); zip.closeEntry()
            data.background?.let { zip.putNextEntry(ZipEntry("background.jpg")); zip.write(it); zip.closeEntry() }
        }
    }
    fun read(input: InputStream): BackupData {
        var json: ByteArray? = null; var image: ByteArray? = null; var total = 0
        val names = mutableSetOf<String>()
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory && entry.name in setOf("backup.json", "background.jpg") && names.add(entry.name)) { "备份文件结构不正确。" }
                val out = ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while (true) { val count = zip.read(buffer); if (count < 0) break
                    total += count; require(total <= MAX_FILE) { "备份解压后超过 50 MB。" }; out.write(buffer, 0, count)
                }
                if (entry.name == "backup.json") json = out.toByteArray() else image = out.toByteArray()
                zip.closeEntry()
            }
        }
        return decode(requireNotNull(json) { "没有找到备份数据。" }, image)
    }
}

object ImageStore {
    fun file(context: Context, name: String): File? = name.takeIf { it.matches(Regex("background-[a-f0-9-]+\\.jpg")) }
        ?.let { File(context.filesDir, it) }?.takeIf { it.isFile }
    fun validate(bytes: ByteArray) {
        val info = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, info)
        require(info.outWidth in 1..30_000 && info.outHeight in 1..30_000) { "图片格式无效或尺寸过大。" }
        var sample = 1
        while (maxOf(info.outWidth, info.outHeight) / sample > 128) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        requireNotNull(decoded) { "图片数据无法解码。" }.recycle()
    }
    fun save(context: Context, bytes: ByteArray): String {
        validate(bytes)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1; while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2560) sample *= 2
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })) { "无法读取图片。" }
        val orientation = runCatching { ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)
        val matrix = Matrix().apply { when (orientation) {
            2 -> setScale(-1f, 1f); 3 -> setRotate(180f); 4 -> setScale(1f, -1f)
            5 -> { setRotate(90f); postScale(-1f, 1f) }; 6 -> setRotate(90f)
            7 -> { setRotate(270f); postScale(-1f, 1f) }; 8 -> setRotate(270f)
        } }
        val rotated = if (matrix.isIdentity) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        val name = "background-${UUID.randomUUID()}.jpg"; val target = File(context.filesDir, name)
        try { target.outputStream().use { check(rotated.compress(Bitmap.CompressFormat.JPEG, 90, it)) }; return name }
        catch (error: Exception) { target.delete(); throw error }
        finally { if (rotated !== bitmap) rotated.recycle(); bitmap.recycle() }
    }
    fun readLimited(input: InputStream, limit: Int = 40 * 1024 * 1024): ByteArray = input.use {
        val out = ByteArrayOutputStream(); val buf = ByteArray(8192)
        while (true) { val n = it.read(buf); if (n < 0) break; require(out.size() + n <= limit) { "所选文件过大。" }; out.write(buf, 0, n) }
        out.toByteArray()
    }
}

class BackupManager(private val context: Context, private val repository: FocusRepository) {
    suspend fun export(uri: Uri) = withContext(Dispatchers.IO) {
        val settings = repository.currentSettings()
        val used = repository.currentGardenUsed()
        val data = repository.database.withTransaction { BackupData(repository.dao.allTasks(), repository.dao.allRecords().map { it.toDomain() }, settings,
            ImageStore.file(context, settings.backgroundFile)?.readBytes(), gardenUsed = Garden.validUsed(repository.dao.allRecords().map { it.toDomain() }, used)) }
        requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { BackupCodec.write(it, data) }
    }
    suspend fun read(uri: Uri): BackupData = withContext(Dispatchers.IO) {
        requireNotNull(context.contentResolver.openInputStream(uri)).use { BackupCodec.read(it) }
    }
    suspend fun merge(data: BackupData, restoreSettings: Boolean): ImportResult = withContext(Dispatchers.IO) {
        // Decode and validate before this entry point; staging the image precedes database changes.
        val imageName = if (restoreSettings && data.background != null) ImageStore.save(context, data.background) else ""
        val plan = try { repository.database.withTransaction {
            val merge = BackupMerge.plan(repository.dao.allTasks(), repository.dao.allRecords().map { it.toDomain() }, data)
            merge.tasks.forEach { repository.dao.saveTask(it) }
            merge.records.forEach { repository.dao.insertRecord(RecordEntity.from(it)) }; merge
        } } catch (e: Exception) { ImageStore.file(context, imageName)?.delete(); throw e }
        var settingsError = false
        if (restoreSettings) {
            try { val old = repository.currentSettings().backgroundFile
                repository.updateSettings { data.settings.copy(backgroundFile = imageName) }; ImageStore.file(context, old)?.delete() }
            catch (e: Exception) { settingsError = true; ImageStore.file(context, imageName)?.delete() }
        }
        val gardenRestored = data.gardenUsed?.let { used ->
            runCatching { repository.mergeGardenUsed(Garden.validUsed(repository.dao.allRecords().map { it.toDomain() }, used)) }.isSuccess
        }
        runCatching { repository.reconcileSelectedTask() }
        return@withContext ImportResult(plan.tasks.size, plan.records.size, plan.duplicates, plan.conflicts,
            if (restoreSettings) !settingsError else null, settingsError, gardenRestored)
    }
}
