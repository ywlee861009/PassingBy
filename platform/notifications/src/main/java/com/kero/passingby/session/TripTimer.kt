package com.kero.passingby.session

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/**
 * 여행 중 남은 시간 알림과 도착·쉬는 시간 끝 알람(명세 10.5). 포그라운드 서비스 없이 일반 알림과 AlarmManager로 한다.
 * 남은 시간은 알림의 거꾸로 세는 시계를 시스템이 그리고 알람도 앱 프로세스 없이 울리므로, 백그라운드에서 프로세스가 종료되어도 둘 다 이어진다.
 * 여정 시계는 elapsedRealtime 기준이라 화면이 다시 열리면 뷰모델이 저장된 여행을 이어받는다.
 *
 * 알람은 부정확 알람(setAndAllowWhileIdle)이라 시스템이 남은 시간에 비례해 늦출 수 있고, 깊은 절전에서는 더 늦다.
 * 도착 알림은 "그 무렵"의 안내로 기획했으므로 정확한 알람 권한은 쓰지 않는다(명세 10.5).
 */
object TripTimer {
    private lateinit var createAlarmIntent: (Context) -> Intent

    /** :app supplies the receiver destination; the adapter has no application dependency. */
    fun configure(alarmIntent: (Context) -> Intent) { createAlarmIntent = alarmIntent }

    private const val EXTRA_ALERT_TITLE = "alert_title"
    private const val EXTRA_ALERT_TEXT = "alert_text"

    /**
     * 남은 시간 알림을 띄우거나 내용을 바꾸고 알람을 다시 건다.
     * @param endElapsed 알릴 시각(elapsedRealtime). 0 이하면 카운트다운과 알람을 멈춘다(일시정지).
     * @param remainingMillis 일시정지 중 크게 표시할 고정된 남은 시간.
     */
    fun update(ctx: Context, title: String, text: String, endElapsed: Long, alertTitle: String, alertText: String, remainingMillis: Long? = null) {
        val endWall = if (endElapsed > 0) System.currentTimeMillis() + (endElapsed - SystemClock.elapsedRealtime()) else null
        TripNotifications.ongoing(ctx, title, text, endWall, remainingMillis)
        cancelAlarm(ctx)
        if (endElapsed > 0) scheduleAlarm(ctx, endElapsed, alertTitle, alertText)
    }

    fun stop(ctx: Context) {
        cancelAlarm(ctx)
        TripNotifications.cancelOngoing(ctx)
    }

    private fun alarmIntent(ctx: Context, title: String? = null, text: String? = null): PendingIntent {
        val i = createAlarmIntent(ctx)
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

    fun alertExtras(i: Intent): Pair<String, String> =
        (i.getStringExtra(EXTRA_ALERT_TITLE) ?: "") to (i.getStringExtra(EXTRA_ALERT_TEXT) ?: "")
}

/** 앱 화면이 보이는지. MainActivity가 onStart·onStop에서 바꾼다. */
object AppVisibility {
    @Volatile var foreground = false
}
