package app.yike.focus

import android.app.Application
import app.yike.focus.data.FocusRepository
import app.yike.focus.platform.FocusController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class FocusApplication : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val soundPlayer by lazy { app.yike.focus.platform.FocusSoundPlayer(this, applicationScope) }
    val repository by lazy { FocusRepository(this) }
    val controller by lazy { FocusController(this, repository, applicationScope) }
}
