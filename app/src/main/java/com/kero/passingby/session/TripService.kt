package com.kero.passingby.session

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.kero.passingby.telemetry.Telemetry

/**
 * 여행 중 포그라운드 서비스(명세 10.5). 남은 시간 알림을 띄우고, 도착·쉬는 시간 끝에 알람을 걸어 둔다.
 * 여정 시계는 elapsedRealtime 기준이라 서비스는 시각만 들고 있으면 되고, 화면이 다시 열리면 뷰모델이 이어받는다.
 *
 * 알람은 절전 중에도 울리는 부정확 알람(setAndAllowWhileIdle)이라 깊은 절전에서는 몇 분 늦을 수 있다.
 * 정확한 알람 권한은 쓰지 않는다(명세 10.5).
 */
class TripService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            cancelAlarm(this)
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            // 이 정지 요청 뒤에 시작 요청이 더 와 있으면 내리지 않는다. stopSelf()로 무조건 내리면
            // 아직 처리하지 못한 startForegroundService 요청이 startForeground를 못 불러 앱이 종료된다.
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: ""
        val text = intent?.getStringExtra(EXTRA_TEXT) ?: ""
        val endElapsed = intent?.getLongExtra(EXTRA_END_ELAPSED, -1L) ?: -1L
        val endWall = if (endElapsed > 0) System.currentTimeMillis() + (endElapsed - SystemClock.elapsedRealtime()) else null
        val n = TripNotifications.ongoing(this, title, text, endWall)
        ServiceCompat.startForeground(this, TripNotifications.ONGOING_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        cancelAlarm(this)
        if (endElapsed > 0) {
            val alertTitle = intent?.getStringExtra(EXTRA_ALERT_TITLE) ?: ""
            val alertText = intent?.getStringExtra(EXTRA_ALERT_TEXT) ?: ""
            scheduleAlarm(this, endElapsed, alertTitle, alertText)
        }
        return START_NOT_STICKY
    }

    companion object {
        private const val ACTION_STOP = "stop"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_TEXT = "text"
        private const val EXTRA_END_ELAPSED = "end"
        private const val EXTRA_ALERT_TITLE = "alert_title"
        private const val EXTRA_ALERT_TEXT = "alert_text"

        /**
         * 서비스를 시작하거나 알림 내용을 바꾼다.
         * @param endElapsed 알릴 시각(elapsedRealtime). 0 이하면 시계와 알람 없이 문구만 띄운다(일시정지).
         */
        fun update(ctx: Context, title: String, text: String, endElapsed: Long, alertTitle: String, alertText: String) {
            val i = Intent(ctx, TripService::class.java)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_TEXT, text)
                .putExtra(EXTRA_END_ELAPSED, endElapsed)
                .putExtra(EXTRA_ALERT_TITLE, alertTitle)
                .putExtra(EXTRA_ALERT_TEXT, alertText)
            // 백그라운드에서 시작하면 Android 12+가 막는다(ForegroundServiceStartNotAllowedException). 앱은 계속 쓰게 두고 기록만 남긴다.
            runCatching { ContextCompat.startForegroundService(ctx, i) }.onFailure { Telemetry.error(it, "fgs_start") }
        }

        fun stop(ctx: Context) {
            cancelAlarm(ctx)
            runCatching { ctx.startService(Intent(ctx, TripService::class.java).setAction(ACTION_STOP)) }
                .onFailure { Telemetry.error(it, "fgs_stop") }
        }

        private fun alarmIntent(ctx: Context, title: String? = null, text: String? = null): PendingIntent {
            val i = Intent(ctx, TripAlarmReceiver::class.java)
            if (title != null) i.putExtra(EXTRA_ALERT_TITLE, title).putExtra(EXTRA_ALERT_TEXT, text)
            return PendingIntent.getBroadcast(ctx, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        private fun scheduleAlarm(ctx: Context, atElapsed: Long, title: String, text: String) {
            val am = ctx.getSystemService(AlarmManager::class.java) ?: return
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, atElapsed, alarmIntent(ctx, title, text))
        }

        private fun cancelAlarm(ctx: Context) {
            ctx.getSystemService(AlarmManager::class.java)?.cancel(alarmIntent(ctx))
        }

        internal fun alertExtras(i: Intent): Pair<String, String> =
            (i.getStringExtra(EXTRA_ALERT_TITLE) ?: "") to (i.getStringExtra(EXTRA_ALERT_TEXT) ?: "")
    }
}

/** 도착·쉬는 시간 끝 알람. 앱이 화면에 떠 있으면 화면이 직접 알리므로 알림을 띄우지 않는다. */
class TripAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (AppVisibility.foreground) return
        val (title, text) = TripService.alertExtras(intent)
        if (title.isNotEmpty()) TripNotifications.alert(context, title, text)
        // 거꾸로 세던 알림이 0을 지나 음수로 가지 않게 서비스를 내린다. 화면을 다시 열면 뷰모델이 이어받는다.
        TripService.stop(context)
    }
}

/** 앱 화면이 보이는지. MainActivity가 onStart·onStop에서 바꾼다. */
object AppVisibility {
    @Volatile var foreground = false
}
