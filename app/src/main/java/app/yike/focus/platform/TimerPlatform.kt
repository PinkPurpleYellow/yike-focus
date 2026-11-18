package app.yike.focus.platform

import android.annotation.SuppressLint
import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.yike.focus.FocusApplication
import app.yike.focus.MainActivity
import app.yike.focus.R
import app.yike.focus.domain.*
import kotlinx.coroutines.launch

object TimerPlatform {
    const val ALARM = "app.yike.focus.COMPLETE"
    const val PAUSE = "app.yike.focus.PAUSE"
    const val RESUME = "app.yike.focus.RESUME"

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 10, Intent(context, TimerReceiver::class.java).setAction(ALARM),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    fun refresh(context: Context, state: TimerState, settings: FocusSettings) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        alarm.cancel(alarmIntent(context))
        if (state.status == RunStatus.RUNNING) {
            try {
                alarm.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, state.deadlineElapsedMs, alarmIntent(context))
            } catch (_: SecurityException) {
                alarm.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, state.deadlineElapsedMs, alarmIntent(context))
            }
            runCatching { ContextCompat.startForegroundService(context, Intent(context, TimerService::class.java)) }
        } else {
            context.stopService(Intent(context, TimerService::class.java))
            if (state.status == RunStatus.PAUSED) NotificationKit.showPaused(context, state)
            else NotificationKit.cancelActive(context)
        }
    }
}

class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext as FocusApplication
        app.applicationScope.launch {
            try {
                if (!app.repository.hasConsent()) return@launch
                app.controller.handle(when (intent.action) {
                    TimerPlatform.PAUSE -> TimerAction.PAUSE
                    TimerPlatform.RESUME -> TimerAction.START
                    else -> TimerAction.ALARM
                })
            } finally { pending.finish() }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        val app = context.applicationContext as FocusApplication
        app.applicationScope.launch {
            try { if (app.repository.hasConsent()) app.controller.handle(TimerAction.ALARM) } finally { pending.finish() }
        }
    }
}

object NotificationKit {
    const val ACTIVE_ID = 100
    private const val FINISHED_ID = 101
    private const val ACTIVE_CHANNEL = "focus_progress_v1"

    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)
    private fun allowed(context: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun launch(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    private fun control(context: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
        context, if (action == TimerPlatform.PAUSE) 20 else 21,
        Intent(context, TimerReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun ongoing(context: Context, state: TimerState): Notification {
        manager(context).createNotificationChannel(NotificationChannel(ACTIVE_CHANNEL, "计时进度", NotificationManager.IMPORTANCE_LOW).apply {
            description = "显示当前倒计时，并提供暂停和继续操作"
            setSound(null, null); enableVibration(false); setShowBadge(false)
        })
        val paused = state.status == RunStatus.PAUSED
        val remaining = state.remaining(SystemClock.elapsedRealtime())
        val time = "%02d:%02d".format((remaining + 999) / 60_000, ((remaining + 999) / 1_000) % 60)
        val builder = NotificationCompat.Builder(context, ACTIVE_CHANNEL)
            .setSmallIcon(R.drawable.ic_tomato)
            .setContentTitle(if (paused) "${state.phase.title}已暂停" else "${state.phase.title}进行中")
            .setContentText(if (paused) "剩余 $time · 点击继续" else if (state.phase == Phase.FOCUS) state.taskTitle else "放松一下，下一轮由你开启")
            .setContentIntent(launch(context))
            .setOnlyAlertOnce(true)
            .setOngoing(!paused)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(0xFFC75C47.toInt())
            .addAction(0, if (paused) "继续" else "暂停", control(context, if (paused) TimerPlatform.RESUME else TimerPlatform.PAUSE))
        if (!paused) builder.setWhen(System.currentTimeMillis() + remaining).setUsesChronometer(true).setChronometerCountDown(true)
        else builder.setShowWhen(false)
        return builder.build()
    }

    @SuppressLint("MissingPermission") // Runtime notification permission is checked by allowed().
    fun showPaused(context: Context, state: TimerState) {
        if (allowed(context)) manager(context).notify(ACTIVE_ID, ongoing(context, state))
    }
    fun cancelActive(context: Context) { manager(context).cancel(ACTIVE_ID) }

    @SuppressLint("MissingPermission") // Runtime notification permission is checked by allowed().
    fun finished(context: Context, phase: Phase, settings: FocusSettings) {
        if (!allowed(context)) return
        val channelId = "completion_s${if (settings.sound) 1 else 0}_v${if (settings.vibration) 1 else 0}"
        manager(context).createNotificationChannel(NotificationChannel(channelId, "完成提醒 · ${if (settings.sound) "有声" else "静音"}${if (settings.vibration) "及振动" else ""}", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "专注与休息结束时的轻柔提醒"
            setSound(if (settings.sound) Uri.parse("android.resource://${context.packageName}/${R.raw.gentle_chime}") else null,
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            enableVibration(settings.vibration)
            if (settings.vibration) vibrationPattern = longArrayOf(0, 140, 100, 140)
        })
        manager(context).notify(FINISHED_ID, NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_tomato)
            .setContentTitle(if (phase == Phase.FOCUS) "这一刻，完成了" else "休息结束，准备好再出发")
            .setContentText(if (phase == Phase.FOCUS) "已记下这轮专注，休息计时已开始。" else "下一轮专注等待你手动开始。")
            .setContentIntent(launch(context)).setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER).setColor(0xFFC75C47.toInt()).build())
    }
}
