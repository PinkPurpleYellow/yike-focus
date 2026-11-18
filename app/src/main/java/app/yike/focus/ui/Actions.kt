package app.yike.focus.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

fun CoroutineScope.launchSafe(onFailure: (Throwable) -> Unit, block: suspend () -> Unit) = launch {
    try { block() } catch (error: Exception) { onFailure(error) }
}
