package com.kero.passingby

import android.app.Application
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.kero.passingby.experience.ExperienceProgram
import com.kero.passingby.experience.ExperienceRegistry
import com.kero.passingby.session.CompletionEffect
import com.kero.passingby.feature.journey.R
import com.kero.passingby.journey.SessionEvent
import com.kero.passingby.journey.SessionPhase
import com.kero.passingby.journey.SessionRuntime
import com.kero.passingby.locale.AppLanguage
import com.kero.passingby.session.AppVisibility
import com.kero.passingby.session.TripNotifications
import com.kero.passingby.session.TripTimer
import com.kero.passingby.telemetry.Telemetry

/** App-lifetime effects consume committed transitions, including alarm-only process starts. */
internal class JourneySessionEffects(private val app: Application, private val runtime: SessionRuntime, private val registry: ExperienceRegistry) {
    private var syncedVersion = -1L
    init {
        runtime.observe { event ->
            if (runtime.checkpointVersion != syncedVersion) syncTimer()
            if (event != null) {
                Telemetry.state(runtime.state.phase.name, runtime.program.courseId, runtime.state.tripMinutes, runtime.program.direction)
                when (event) {
                    is SessionEvent.Arrived -> {
                        Telemetry.event("trip_arrive", "course" to runtime.program.courseId, "minutes" to runtime.state.tripMinutes, "late" to event.late)
                        flushPending(event.late)
                    }
                    is SessionEvent.RestFinished -> {
                        Telemetry.event("rest_end", "course" to runtime.program.courseId, "late" to event.late)
                        flushPending(event.late)
                    }
                    is SessionEvent.Departed -> Telemetry.event("trip_start", "course" to runtime.program.courseId,
                        "minutes" to runtime.state.tripMinutes, "direction" to runtime.program.direction, "from" to event.from.name.lowercase())
                    is SessionEvent.Paused -> Telemetry.event("trip_pause", "course" to runtime.program.courseId, "progress" to event.progressPercent)
                    SessionEvent.Resumed -> Telemetry.event("trip_resume", "course" to runtime.program.courseId)
                    is SessionEvent.Ended -> Telemetry.event("trip_end_early", "course" to runtime.program.courseId,
                        "minutes" to event.tripMinutes, "progress" to event.progressPercent)
                    SessionEvent.RestStarted -> Telemetry.event("rest_start", "course" to runtime.program.courseId)
                    is SessionEvent.GotOff -> Telemetry.event("trip_get_off", "course" to runtime.program.courseId, "from" to event.from.name.lowercase())
                }
            }
        }
    }

    fun syncTimer() {
        syncedVersion = runtime.checkpointVersion
        val res = AppLanguage.localized(app).resources
        val program = runtime.program as ExperienceProgram
        val course = program.course
        val direction = program.direction
        val title = course.title(res, direction)
        val terminus = res.getString(course.ordered(direction).last().name)
        val state = runtime.state
        when (state.phase) {
            SessionPhase.RUNNING -> {
                TripTimer.update(app, title, res.getString(R.string.notif_running, course.stopsLine(res, direction), terminus),
                    if (runtime.scale == 1.0) runtime.deadlineElapsedMillis ?: 0L else 0L,
                    res.getString(R.string.arrived_at, terminus), res.getString(R.string.notif_arrived, state.tripMinutes))
            }
            SessionPhase.PAUSED -> {
                val leftMillis = ((runtime.durationSeconds - runtime.elapsedSeconds).coerceAtLeast(0.0) * 1000).toLong()
                TripTimer.update(app, title, res.getString(R.string.notif_paused, terminus),
                    if (runtime.scale == 1.0) runtime.deadlineElapsedMillis ?: 0L else 0L,
                    res.getString(R.string.arrived_at, terminus), res.getString(R.string.notif_arrived, state.tripMinutes),
                    remainingMillis = leftMillis)
            }
            SessionPhase.RESTING -> {
                val next = course.title(res, -direction)
                TripTimer.update(app, res.getString(R.string.resting_at, terminus), res.getString(R.string.notif_next_trip, next),
                    state.restEndElapsedMillis, res.getString(R.string.rest_over), res.getString(R.string.notif_ticket_ready, next))
            }
            else -> TripTimer.stop(app)
        }
    }

    fun flushPending(late: Boolean = true) {
        val res = AppLanguage.localized(app).resources
        for (effect in runtime.pendingEffects) {
            val course = registry.courseById(effect.courseId)
            val arrived = effect.kind == CompletionEffect.Kind.ARRIVED
            val title = if (arrived) res.getString(R.string.arrived_at, res.getString(course.ordered(effect.direction).last().name))
                else res.getString(R.string.rest_over)
            val text = if (arrived) res.getString(R.string.notif_arrived, effect.minutes)
                else res.getString(R.string.notif_ticket_ready, course.title(res, effect.direction))
            val pattern = if (arrived) longArrayOf(0, 180, 140, 180, 140, 320) else longArrayOf(0, 120, 120, 120)
            if (deliver(late, pattern, title, text, effect.id)) runtime.acknowledgeEffect(effect.id)
        }
    }

    private fun deliver(late: Boolean, pattern: LongArray, title: String, text: String, id: String): Boolean {
        if (!AppVisibility.foreground) return TripNotifications.alert(app, title, text, id)
        if (late) return true
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
        RingtoneManager.getRingtone(app, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))?.apply {
            audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            play()
        }
        return true
    }
}
