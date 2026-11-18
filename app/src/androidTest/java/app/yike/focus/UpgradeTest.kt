package app.yike.focus

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.yike.focus.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpgradeTest {
    @Test fun originalDatabaseMigratesWithoutDroppingTasksOrHistory() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "migration-verification.db"
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name); path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            old.execSQL("CREATE TABLE IF NOT EXISTS tasks (id TEXT NOT NULL, title TEXT NOT NULL, createdAt INTEGER NOT NULL, completed INTEGER NOT NULL, deleted INTEGER NOT NULL, PRIMARY KEY(id))")
            old.execSQL("CREATE TABLE IF NOT EXISTS focus_records (id TEXT NOT NULL, taskId TEXT, taskTitle TEXT NOT NULL, startedAtMs INTEGER NOT NULL, endedAtMs INTEGER NOT NULL, focusedMs INTEGER NOT NULL, completed INTEGER NOT NULL, spansJson TEXT NOT NULL, PRIMARY KEY(id))")
            old.execSQL("INSERT INTO tasks VALUES ('task1', '旧版任务', 123, 1, 0)")
            old.execSQL("INSERT INTO focus_records VALUES ('session1', 'task1', '历史名称', 1000, 2000, 1000, 1, '[[1000,2000]]')")
            old.version = 1
        }
        val database = Room.databaseBuilder(context, FocusDatabase::class.java, name).addMigrations(MIGRATION_1_2).build()
        try {
            val task = database.dao().allTasks().single()
            assertEquals("旧版任务", task.title); assertEquals(123L, task.updatedAt); assertTrue(task.completed)
            val record = database.dao().allRecords().single().toDomain()
            assertEquals("历史名称", record.taskTitle); assertEquals(1000L, record.focusedMs)
            assertEquals(2, database.openHelper.readableDatabase.version)
        } finally { database.close(); context.deleteDatabase(name) }
    }
}
