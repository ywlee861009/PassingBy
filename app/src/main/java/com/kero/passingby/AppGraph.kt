package com.kero.passingby

import android.app.Application
import com.kero.passingby.journey.SessionScheduler
import com.kero.passingby.journey.SessionRuntime
import com.kero.passingby.experience.TrainExperienceProvider
import com.kero.passingby.experience.airplane.AirplaneExperienceProvider
import com.kero.passingby.experience.ship.ShipExperienceProvider
import com.kero.passingby.experience.spaceship.SpaceExperienceProvider
import com.kero.passingby.experience.ExperienceRegistry
import android.os.SystemClock
import android.os.Looper
import android.os.Handler
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
    private val modeAccess = PreviewModeAccess(app)
    val experiences = ExperienceRegistry(listOf(TrainExperienceProvider(), AirplaneExperienceProvider(), ShipExperienceProvider(), SpaceExperienceProvider(app)))
    init {
        val allowed = if (modeAccess.unlocked) experiences.courses else experiences.courses.filter { experiences.modeFor(it.id) in modeAccess.publicModes }
        store.resetUnavailableCourse(allowed.map { it.id }.toSet())
    }
    private val handler = Handler(Looper.getMainLooper())
    private var effects: JourneySessionEffects? = null
    val session by lazy {
        SessionRuntime(store, experiences.create(store.courseId, store),
            SystemClock::elapsedRealtime,
            SessionScheduler { delay, task ->
                val runnable = Runnable { task() }
                val cancellation: () -> Unit = { handler.removeCallbacks(runnable) }
                handler.postDelayed(runnable, delay)
                cancellation
            }).also { runtime ->
                effects = JourneySessionEffects(app, runtime, experiences)
                runtime.start()
                effects?.syncTimer()
                effects?.flushPending()
            }
    }
    val journeyViewModelFactory = JourneyViewModel.Factory(
        app, JourneyOptions(developerTools = BuildConfig.DEV_TOOLS, modeAccess = modeAccess), store, { session }, experiences,
    )

    fun refreshAlarm() { session.refresh(); effects?.syncTimer(); effects?.flushPending() }
    fun closeSession() { session.close(); session.program.let { (it as? AutoCloseable)?.close() } }

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
