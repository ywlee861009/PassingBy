package com.kero.passingby.session

import com.kero.passingby.platform.notifications.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kero.passingby.telemetry.Telemetry
import java.util.Locale
import kotlin.math.ceil

/** 여행 알림 두 가지: 여행 중 계속 떠 있는 남은 시간 알림, 도착·쉬는 시간 끝 알림. */
object TripNotifications {
    private lateinit var createOpenApp: (Context) -> PendingIntent
    private var localizedContext: (Context) -> Context = { it }

    fun configure(openApp: (Context) -> PendingIntent, localize: (Context) -> Context) {
        createOpenApp = openApp
        localizedContext = localize
    }

    private const val ONGOING_ID = 1
    private const val ALERT_ID = 2
    private const val CH_TRIP = "trip"
    private const val CH_ALERT = "alert"

    /** App-wide permission and both user-controlled travel channels must be available. */
    fun areEnabled(ctx: Context): Boolean =
        NotificationManagerCompat.from(ctx).areNotificationsEnabled() && blockedChannel(ctx) == null

    fun settingsIntent(ctx: Context): Intent {
        val channel = if (NotificationManagerCompat.from(ctx).areNotificationsEnabled()) blockedChannel(ctx) else null
        return Intent(if (channel == null) Settings.ACTION_APP_NOTIFICATION_SETTINGS else Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
            .apply { if (channel != null) putExtra(Settings.EXTRA_CHANNEL_ID, channel) }
    }

    private fun blockedChannel(ctx: Context): String? {
        val manager = ctx.getSystemService(NotificationManager::class.java) ?: return null
        return listOf(CH_TRIP, CH_ALERT).firstOrNull {
            manager.getNotificationChannel(it)?.importance == NotificationManager.IMPORTANCE_NONE
        }
    }

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        // 이미 있는 채널이면 이름·설명만 바뀐다. 그래서 앱 언어를 바꾸면 다음 알림 때 채널 이름도 따라간다.
        val res = localizedContext(ctx).resources
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
     * 여행 중 알림을 띄우거나 내용을 바꾼다. [endWallMillis]가 있으면 그 시각까지 거꾸로 세는 시계를 붙인다
     * (시스템이 그리므로 1초마다 갱신할 필요 없고, 앱 프로세스가 없어도 줄어든다).
     * 그 시각이 지나면 알림이 저절로 사라진다. 절전 때문에 도착 알람이 늦어져도 시계가 0을 지나 음수로 가지 않는다.
     */
    fun ongoing(ctx: Context, title: String, text: String, endWallMillis: Long?, remainingMillis: Long? = null) {
        ensureChannels(ctx)
        val b = NotificationCompat.Builder(ctx, CH_TRIP)
            .setSmallIcon(R.drawable.ic_stat_window)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(openApp(ctx))
        if (endWallMillis != null || remainingMillis != null) {
            b.setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(timerView(ctx, R.layout.notification_trip, title, text, endWallMillis, remainingMillis))
                .setCustomBigContentView(timerView(ctx, R.layout.notification_trip_expanded, title, text, endWallMillis, remainingMillis))
        }
        if (endWallMillis != null) {
            b.setWhen(endWallMillis)
            val left = endWallMillis - System.currentTimeMillis()
            if (left > 0) b.setTimeoutAfter(left)
        }
        runCatching { ctx.getSystemService(NotificationManager::class.java)?.notify(ONGOING_ID, b.build()) }
            .onFailure { Telemetry.error(it, "notify_ongoing") }
    }

    private fun timerView(ctx: Context, layout: Int, title: String, text: String, endWallMillis: Long?, remainingMillis: Long?): RemoteViews {
        val res = localizedContext(ctx).resources
        return RemoteViews(ctx.packageName, layout).apply {
            setTextViewText(R.id.notification_title, title)
            if (layout == R.layout.notification_trip_expanded) setTextViewText(R.id.notification_text, text)
            setTextViewText(R.id.notification_time_label, res.getString(
                if (endWallMillis != null) R.string.notification_time_remaining else R.string.notification_time_paused,
            ))
            setViewVisibility(R.id.notification_countdown, if (endWallMillis != null) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.notification_paused_time, if (endWallMillis == null) View.VISIBLE else View.GONE)
            if (endWallMillis != null) {
                // SystemUI owns the ticking view, including when the app process is gone.
                val base = SystemClock.elapsedRealtime() + (endWallMillis - System.currentTimeMillis()).coerceAtLeast(0L)
                setChronometerCountDown(R.id.notification_countdown, true)
                setChronometer(R.id.notification_countdown, base, null, true)
            } else {
                setChronometer(R.id.notification_countdown, SystemClock.elapsedRealtime(), null, false)
                val seconds = ceil((remainingMillis ?: 0L).coerceAtLeast(0L) / 1000.0).toLong()
                val clock = if (seconds >= 3600) String.format(Locale.US, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
                    else String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)
                setTextViewText(R.id.notification_paused_time, clock)
            }
        }
    }

    fun cancelOngoing(ctx: Context) {
        ctx.getSystemService(NotificationManager::class.java)?.cancel(ONGOING_ID)
    }

    /** 도착·쉬는 시간 끝 알림. 소리·진동은 채널 설정을 따른다. */
    fun alert(ctx: Context, title: String, text: String, deliveryId: String? = null): Boolean {
        val manager = ctx.getSystemService(NotificationManager::class.java) ?: return false
        val retry = deliveryId != null && manager.activeNotifications.any {
            it.id == ALERT_ID && it.notification.extras.getString("passingby.delivery") == deliveryId
        }
        ensureChannels(ctx)
        val n = NotificationCompat.Builder(ctx, CH_ALERT)
            .setOnlyAlertOnce(retry)
            .addExtras(android.os.Bundle().apply { putString("passingby.delivery", deliveryId) })
            .setSmallIcon(R.drawable.ic_stat_window)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp(ctx))
            .build()
        return runCatching { manager.notify(ALERT_ID, n); true }
            .onFailure { Telemetry.error(it, "notify_alert") }.getOrDefault(false)
    }

    private fun openApp(ctx: Context): PendingIntent = createOpenApp(ctx)
}
