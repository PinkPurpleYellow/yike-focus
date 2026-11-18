package app.yike.focus.ui

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import app.yike.focus.FocusApplication
import app.yike.focus.data.FocusRepository
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking

// The top-level preferencesDataStore delegate survives Robolectric Application instances.
// Reset that cached store explicitly so each garden scenario starts with its own water IDs.
@Suppress("UNCHECKED_CAST")
internal fun resetGardenTestPreferences(app: FocusApplication) = runBlocking {
    val field = FocusRepository::class.java.getDeclaredField("preferences").apply { isAccessible = true }
    val store = field.get(app.repository) as DataStore<Preferences>
    store.edit { it.clear() }
}

internal fun closeTestApplication(app: FocusApplication) {
    // Finish lazy opening first. Closing while Room's invalidation tracker is
    // opening the same helper reverses its two lock orders under Robolectric.
    app.repository.database.openHelper.writableDatabase
    val job = requireNotNull(app.applicationScope.coroutineContext[kotlinx.coroutines.Job])
    job.cancel()
    val main = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
    val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10)
    while (!job.isCompleted && System.nanoTime() < deadline) {
        main.idle()
        Thread.sleep(2)
    }
    check(job.isCompleted) { "Application workers still using Room" }
    // Finish outstanding Room reads before Robolectric resets SQLite connection pointers.
    app.repository.database.close()
}
