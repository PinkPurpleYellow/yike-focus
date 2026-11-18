package app.yike.focus.platform

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.*
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import app.yike.focus.FocusApplication
import app.yike.focus.R
import app.yike.focus.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine

class TimerService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var audio: FocusSoundPlayer
    private val controller get() = (application as FocusApplication).controller

    override fun onCreate() {
        super.onCreate()
        serviceScope.launch {
            if (!(application as FocusApplication).repository.hasConsent()) { stopSelf(); return@launch }
            audio = (application as FocusApplication).soundPlayer
            promote(controller.state.value, controller.settings.value)
            combine(controller.state, controller.settings, controller.loaded) { state, settings, loaded ->
                Triple(state, settings, loaded)
            }.collect { (state, settings, loaded) ->
                if (!loaded) return@collect
                if (state.status == RunStatus.RUNNING) {
                    promote(state, settings)
                    audio.update(state.phase == Phase.FOCUS, settings)
                } else {
                    audio.update(false, controller.settings.value)
                    stopForeground(STOP_FOREGROUND_DETACH)
                    if (state.status == RunStatus.PAUSED) NotificationKit.showPaused(this@TimerService, state)
                    else NotificationKit.cancelActive(this@TimerService)
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun promote(state: TimerState, settings: FocusSettings) {
        val media = state.status == RunStatus.RUNNING && state.phase == Phase.FOCUS && settings.ambient != Ambient.OFF
        val types = if (Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or if (media) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        } else if (Build.VERSION.SDK_INT >= 29 && media) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        ServiceCompat.startForeground(this, NotificationKit.ACTIVE_ID, NotificationKit.ongoing(this, state), types)
    }

    override fun onDestroy() {
        if (!::audio.isInitialized) { serviceScope.cancel(); super.onDestroy(); return }
        audio.update(false, controller.settings.value)
        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_DETACH)
        when (controller.state.value.status) {
            RunStatus.PAUSED -> NotificationKit.showPaused(this, controller.state.value)
            RunStatus.READY -> NotificationKit.cancelActive(this)
            RunStatus.RUNNING -> Unit
        }
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}

