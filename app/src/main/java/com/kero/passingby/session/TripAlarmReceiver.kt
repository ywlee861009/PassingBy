package com.kero.passingby.session

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 도착·쉬는 시간 끝 알람. 앱이 화면에 떠 있으면 화면이 직접 알리므로 알림을 띄우지 않는다. */
class TripAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (AppVisibility.foreground) return
        val (title, text) = TripTimer.alertExtras(intent)
        if (title.isNotEmpty()) TripNotifications.alert(context, title, text)
        // 남은 시간 알림을 내린다. 화면을 다시 열면 뷰모델이 이어받는다.
        TripTimer.stop(context)
    }
}
