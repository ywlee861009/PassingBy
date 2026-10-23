package com.kero.passingby.session

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kero.passingby.PassingByApp

/** Reconciles persisted deadlines even when no Activity or ViewModel has been created. */
class TripAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        (context.applicationContext as PassingByApp).graph.refreshAlarm()
    }
}
