package com.kero.passingby

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import com.kero.passingby.locale.AppLanguage
import com.kero.passingby.session.TripAlarmReceiver
import com.kero.passingby.session.TripNotifications
import com.kero.passingby.session.TripStore
import com.kero.passingby.session.TripTimer
import com.kero.passingby.ui.journey.JourneyOptions
import com.kero.passingby.ui.journey.JourneyViewModel

/** Application composition root. Android entry-point classes stay in :app. */
class AppGraph(app: Application) {
    val store = TripStore(app)
    val journeyViewModelFactory = JourneyViewModel.Factory(
        app, JourneyOptions(developerTools = BuildConfig.DEV_TOOLS), store,
    )

    init {
        TripTimer.configure { context -> Intent(context, TripAlarmReceiver::class.java) }
        TripNotifications.configure(
            openApp = { context ->
                PendingIntent.getActivity(
                    context, 0,
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            },
            localize = AppLanguage::localized,
        )
    }
}
