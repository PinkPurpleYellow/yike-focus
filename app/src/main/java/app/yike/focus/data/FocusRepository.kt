package app.yike.focus.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.*
import app.yike.focus.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private val Context.focusPreferences by preferencesDataStore("yike_preferences")

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val completed: Boolean = false,
    val deleted: Boolean = false,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = createdAt,
)

@Entity(tableName = "focus_records")
data class RecordEntity(
    @PrimaryKey val id: String,
    val taskId: String?,
    val taskTitle: String,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val focusedMs: Long,
    val completed: Boolean,
    val spansJson: String,
) {
    fun toDomain() = FocusRecord(id, taskId, taskTitle, startedAtMs, endedAtMs, focusedMs, completed, JsonCodec.readSpans(JSONArray(spansJson)))
    companion object {
        fun from(record: FocusRecord) = RecordEntity(
            record.id, record.taskId, record.taskTitle, record.startedAtMs, record.endedAtMs,
            record.focusedMs, record.completed, JsonCodec.spans(record.spans).toString(),
        )
    }
}

@Dao
interface FocusDao {
    @Query("SELECT * FROM tasks WHERE deleted = 0 ORDER BY completed ASC, createdAt DESC")
    fun observeTasks(): Flow<List<TaskEntity>>
    @Query("SELECT * FROM tasks WHERE id = :id AND deleted = 0 LIMIT 1")
    suspend fun task(id: String): TaskEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveTask(task: TaskEntity)
    @Query("UPDATE tasks SET deleted = 1, updatedAt = :time WHERE id = :id")
    suspend fun deleteTask(id: String, time: Long)
    @Query("SELECT * FROM tasks")
    suspend fun allTasks(): List<TaskEntity>
    @Query("SELECT * FROM focus_records")
    suspend fun allRecords(): List<RecordEntity>
    @Query("SELECT * FROM focus_records ORDER BY endedAtMs DESC")
    fun observeRecords(): Flow<List<RecordEntity>>
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecord(record: RecordEntity): Long
}

@Database(entities = [TaskEntity::class, RecordEntity::class], version = 2, exportSchema = true)
abstract class FocusDatabase : RoomDatabase() { abstract fun dao(): FocusDao }

class FocusRepository(context: Context) {
    private val preferences = context.focusPreferences
    internal val database by lazy { Room.databaseBuilder(context, FocusDatabase::class.java, "yike.db").addMigrations(MIGRATION_1_2).build() }
    val dao get() = database.dao()
    val tasks get() = dao.observeTasks()
    val records get() = dao.observeRecords().map { entities -> entities.map(RecordEntity::toDomain) }
    val legalConsentVersion = preferences.data.map { it[Keys.consent] ?: 0 }.distinctUntilChanged()
    suspend fun setLegalConsentVersion(version: Int) { preferences.edit { it[Keys.consent] = version } }
    suspend fun hasConsent() = legalConsentVersion.first() >= LEGAL_VERSION
    val workspace = preferences.data.map { it[Keys.workspace] ?: "{}" }.distinctUntilChanged()
    suspend fun reviseWorkspace(transform: (JSONObject) -> Unit) {
        preferences.edit { prefs ->
            val json = JSONObject(prefs[Keys.workspace] ?: "{}")
            transform(json)
            prefs[Keys.workspace] = json.toString()
        }
    }
    val settings = preferences.data.map(::decodeSettings).distinctUntilChanged()
    val gardenUsed = preferences.data.map { it[Keys.gardenUsed] ?: emptySet() }.distinctUntilChanged()
    val navDock = preferences.data.map { enumOrDefault(it[Keys.navDock], NavDock.BOTTOM) }.distinctUntilChanged()
    suspend fun setNavDock(dock: NavDock) { preferences.edit { it[Keys.navDock] = dock.name } }
    suspend fun currentGardenUsed(): Set<String> = preferences.data.first()[Keys.gardenUsed] ?: emptySet()
    suspend fun mergeGardenUsed(incoming: Set<String>) { preferences.edit { p ->
        p[Keys.gardenUsed] = (p[Keys.gardenUsed] ?: emptySet()) + incoming
    } }
    suspend fun waterGarden(): Boolean {
        val earned = Garden.earned(dao.allRecords().map { it.toDomain() })
        var watered = false
        preferences.edit { p ->
            val used = p[Keys.gardenUsed] ?: emptySet()
            val next = (earned - used).sorted().firstOrNull()
            if (next != null) { p[Keys.gardenUsed] = used + next; watered = true }
        }
        return watered
    }
    val selectedTaskId = preferences.data.map { it[Keys.selectedTask] }.distinctUntilChanged()

    suspend fun currentSettings() = decodeSettings(preferences.data.first())
    suspend fun readSnapshot(): TimerState? = preferences.data.first()[Keys.snapshot]?.let {
        runCatching { JsonCodec.readState(it) }.getOrNull()
    }
    suspend fun saveSnapshot(state: TimerState) { preferences.edit { it[Keys.snapshot] = JsonCodec.state(state) } }
    suspend fun selectTask(id: String?) {
        preferences.edit { if (id == null) it.remove(Keys.selectedTask) else it[Keys.selectedTask] = id }
    }
    suspend fun selectedTask(): TaskEntity? = preferences.data.first()[Keys.selectedTask]?.let { dao.task(it) }?.takeUnless { it.completed }
    suspend fun reconcileSelectedTask() {
        val id = preferences.data.first()[Keys.selectedTask] ?: return
        val task = dao.task(id)
        if (task == null || task.completed) selectTask(null)
    }
    suspend fun saveRecord(record: FocusRecord) { dao.insertRecord(RecordEntity.from(record)) }
    suspend fun saveTask(task: TaskEntity) {
        dao.saveTask(task.copy(title = task.title.trim().take(120), updatedAt = System.currentTimeMillis()))
        if (task.completed && preferences.data.first()[Keys.selectedTask] == task.id) selectTask(null)
    }
    suspend fun deleteTask(id: String) {
        dao.deleteTask(id, System.currentTimeMillis())
        if (preferences.data.first()[Keys.selectedTask] == id) selectTask(null)
    }
    suspend fun updateSettings(transform: (FocusSettings) -> FocusSettings) {
        preferences.edit { p ->
            val s = transform(decodeSettings(p))
            p[Keys.focus] = s.focusMinutes.coerceIn(1, 180)
            p[Keys.short] = s.shortBreakMinutes.coerceIn(1, 180)
            p[Keys.long] = s.longBreakMinutes.coerceIn(1, 180)
            p[Keys.interval] = s.longBreakEvery.coerceIn(1, 12)
            p[Keys.goal] = s.dailyGoal.coerceIn(1, 40)
            p[Keys.sound] = s.sound
            p[Keys.vibration] = s.vibration
            p[Keys.awake] = s.keepAwake
            p[Keys.ambient] = s.ambient.name
            p[Keys.volume] = s.ambientVolume.coerceIn(0f, 1f)
            p[Keys.theme] = s.themeStyle.name
            p[Keys.mode] = s.themeMode.name
            p[Keys.background] = s.backgroundFile
            p[Keys.shade] = s.backgroundShade.coerceIn(0.25f, 0.95f)
            p[Keys.preset] = s.uiPreset.name
            p[Keys.face] = s.timerFace.name
            p[Keys.density] = s.uiDensity.name
            p[Keys.motion] = s.decorativeMotion
            p[Keys.material] = s.interfaceMaterial.name
            p[Keys.transparency] = s.glassTransparency.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0.5f
            p[Keys.blur] = s.glassBlur.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0.6f
            p[Keys.readable] = s.glassReadable
        }
    }

    private fun decodeSettings(p: Preferences) = FocusSettings(
        p[Keys.focus] ?: 25, p[Keys.short] ?: 5, p[Keys.long] ?: 15,
        p[Keys.interval] ?: 4, p[Keys.goal] ?: 8, p[Keys.sound] ?: true,
        p[Keys.vibration] ?: false, p[Keys.awake] ?: false,
        runCatching { Ambient.valueOf(p[Keys.ambient] ?: "OFF") }.getOrDefault(Ambient.OFF),
        p[Keys.volume] ?: 0.18f,
        runCatching { ThemeStyle.valueOf(p[Keys.theme] ?: "TOMATO") }.getOrDefault(ThemeStyle.TOMATO),
        runCatching { ThemeMode.valueOf(p[Keys.mode] ?: "LIGHT") }.getOrDefault(ThemeMode.LIGHT),
        p[Keys.background] ?: "", p[Keys.shade] ?: 0.65f,
        enumOrDefault(p[Keys.preset], UiPreset.CLASSIC),
        enumOrDefault(p[Keys.face], TimerFace.FOLLOW_PRESET),
        enumOrDefault(p[Keys.density], UiDensity.COMFORTABLE),
        p[Keys.motion] ?: true,
        enumOrDefault(p[Keys.material], InterfaceMaterial.TRADITIONAL),
        (p[Keys.transparency] ?: 0.5f).coerceIn(0f, 1f),
        (p[Keys.blur] ?: 0.6f).coerceIn(0f, 1f),
        p[Keys.readable] ?: false,
    )

    companion object { const val LEGAL_VERSION = 1 }
    private object Keys {
        val workspace = stringPreferencesKey("workspace_v1")
        val consent = intPreferencesKey("legal_consent_version")
        val material = stringPreferencesKey("interface_material")
        val transparency = floatPreferencesKey("glass_transparency")
        val blur = floatPreferencesKey("glass_blur")
        val readable = booleanPreferencesKey("glass_readable")
        val navDock = stringPreferencesKey("navigation_dock")
        val gardenUsed = stringSetPreferencesKey("garden_used_record_ids")
        val focus = intPreferencesKey("focus_minutes")
        val short = intPreferencesKey("short_minutes")
        val long = intPreferencesKey("long_minutes")
        val interval = intPreferencesKey("long_every")
        val goal = intPreferencesKey("daily_goal")
        val sound = booleanPreferencesKey("sound")
        val vibration = booleanPreferencesKey("vibration")
        val awake = booleanPreferencesKey("keep_awake")
        val ambient = stringPreferencesKey("ambient")
        val volume = floatPreferencesKey("ambient_volume")
        val snapshot = stringPreferencesKey("timer_snapshot_v1")
        val selectedTask = stringPreferencesKey("selected_task")
        val theme = stringPreferencesKey("theme_style")
        val mode = stringPreferencesKey("theme_mode")
        val background = stringPreferencesKey("background_file")
        val shade = floatPreferencesKey("background_shade")
        val preset = stringPreferencesKey("ui_preset")
        val face = stringPreferencesKey("timer_face")
        val density = stringPreferencesKey("ui_density")
        val motion = booleanPreferencesKey("decorative_motion")
    }
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE tasks SET updatedAt = createdAt")
    }
}

object JsonCodec {
    fun spans(spans: List<FocusSpan>) = JSONArray().apply {
        spans.forEach { put(JSONArray().put(it.startMs).put(it.endMs)) }
    }
    fun readSpans(json: JSONArray): List<FocusSpan> = (0 until json.length()).map {
        val item = json.getJSONArray(it)
        FocusSpan(item.getLong(0), item.getLong(1))
    }
    fun state(s: TimerState): String = JSONObject().apply {
        put("phase", s.phase.name); put("status", s.status.name)
        put("duration", s.durationMs); put("remaining", s.remainingMs)
        put("deadlineElapsed", s.deadlineElapsedMs); put("deadlineWall", s.deadlineWallMs)
        put("segmentStart", s.segmentStartWallMs); put("startedAt", s.startedAtMs)
        put("sessionId", s.sessionId); put("taskId", s.taskId ?: JSONObject.NULL)
        put("taskTitle", s.taskTitle); put("cycle", s.cycleCompleted)
        put("spans", spans(s.spans)); put("boot", s.bootCount)
        put("checkpoint", s.checkpointElapsedMs)
    }.toString()
    fun readState(text: String): TimerState {
        val j = JSONObject(text)
        return TimerState(
            Phase.valueOf(j.getString("phase")), RunStatus.valueOf(j.getString("status")),
            j.getLong("duration"), j.getLong("remaining"), j.getLong("deadlineElapsed"),
            j.getLong("deadlineWall"), j.getLong("segmentStart"), j.getLong("startedAt"),
            j.getString("sessionId"), if (j.isNull("taskId")) null else j.getString("taskId"),
            j.getString("taskTitle"), j.getInt("cycle"), readSpans(j.getJSONArray("spans")),
            j.getInt("boot"), j.getLong("checkpoint"),
        )
    }
}
