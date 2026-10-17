package com.kero.passingby.session

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.kero.passingby.MainActivity
import com.kero.passingby.R
import com.kero.passingby.locale.AppLanguage

/** 여행 알림 두 가지: 여행 중 계속 떠 있는 남은 시간 알림, 도착·쉬는 시간 끝 알림. */
object TripNotifications {
    const val ONGOING_ID = 1
    private const val ALERT_ID = 2
    private const val CH_TRIP = "trip"
    private const val CH_ALERT = "alert"

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        // 이미 있는 채널이면 이름·설명만 바뀐다. 그래서 앱 언어를 바꾸면 다음 알림 때 채널 이름도 따라간다.
        val res = AppLanguage.localized(ctx).resources
        nm.createNotificationChannel(
            NotificationChannel(CH_TRIP, res.getString(R.string.channel_trip), NotificationManager.IMPORTANCE_LOW).apply {
                description = res.getString(R.string.channel_trip_desc)
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERT, res.getString(R.string.channel_alert), NotificationManager.IMPORTANCE_HIGH).apply {
                description = res.getString(R.string.channel_alert_desc)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 180, 140, 180, 140, 320)
            },
        )
    }

    /**
     * 여행 중 알림. [endWallMillis]가 있으면 그 시각까지 거꾸로 세는 시계를 붙인다(시스템이 그리므로 1초마다 갱신할 필요 없음).
     */
    fun ongoing(ctx: Context, title: String, text: String, endWallMillis: Long?): Notification {
        ensureChannels(ctx)
        val b = NotificationCompat.Builder(ctx, CH_TRIP)
            .setSmallIcon(R.drawable.ic_stat_window)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(openApp(ctx))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (endWallMillis != null) {
            b.setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(endWallMillis)
                .setShowWhen(true)
        }
        return b.build()
    }

    /** 도착·쉬는 시간 끝 알림. 소리·진동은 채널 설정을 따른다. */
    fun alert(ctx: Context, title: String, text: String) {
        ensureChannels(ctx)
        val n = NotificationCompat.Builder(ctx, CH_ALERT)
            .setSmallIcon(R.drawable.ic_stat_window)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp(ctx))
            .build()
        runCatching { ctx.getSystemService(NotificationManager::class.java)?.notify(ALERT_ID, n) }
    }

    private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0,
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
