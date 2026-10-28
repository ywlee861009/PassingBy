package com.kero.passingby.experience.voyage

import com.kero.passingby.experience.*
import com.kero.passingby.journey.*
import com.kero.passingby.session.JourneyStore
import kotlin.math.roundToLong
import kotlin.random.Random

data class VoyageStop(override val name: Int, override val local: String, override val roman: String) : JourneyStation

/** Shared non-stop travel mechanics; each module owns its courses, scene and renderer. */
open class VoyageCourse(
    override val id: String, override val number: Int, override val region: Int,
    override val vehicle: Int, override val stations: List<JourneyStation>,
    override val supportsTimeOfDay: Boolean = true,
) : JourneyCourse {
    init { require(stations.size >= 2) }
    override val productId: String? = null
    override val supportsSeasons = false
    override val stopsEnRoute = false
    open fun positionAt(progress: Float, durationSeconds: Double) = progress * stations.lastIndex
    open fun waypointMarks(durationSeconds: Double) = FloatArray(stations.size) { it.toFloat() / stations.lastIndex }
    open fun progressAtPosition(position: Double, durationSeconds: Double): Double {
        val segment = position.toInt().coerceIn(0, stations.lastIndex - 1)
        val fraction = (position - segment).coerceIn(0.0, 1.0)
        val marks = waypointMarks(durationSeconds)
        return marks[segment] + (marks[segment + 1] - marks[segment]) * fraction
    }
    override fun stopsLine(res: android.content.res.Resources, direction: Int): String {
        val middle = ordered(direction).drop(1).dropLast(1)
        return res.getString(com.kero.passingby.experience.api.R.string.voyage_waypoints, middle.joinToString(" · ") { res.getString(it.name) })
    }
}

class VoyageProgram(
    private val store: JourneyStore,
    private val catalog: List<VoyageCourse>,
    private val makeRenderer: (VoyageScene) -> ExperienceRenderer,
    private val soundKind: AmbientLoop.Kind,
    private val makeAudio: () -> VoyageAudio = { AmbientLoop(soundKind) },
) : ExperienceProgram {
    override var course = catalog.firstOrNull { it.id == store.courseId } ?: catalog.first()
        private set
    override val courseId get() = course.id
    override var direction = if (store.direction < 0) -1 else 1
        private set
    override val scene = VoyageScene(course)
    private var drawing: ExperienceRenderer? = null
    override val renderer get() = drawing ?: makeRenderer(scene).also { drawing = it }
    private var audio: VoyageAudio? = null
    private var running = false
    private var level = store.soundLevel.coerceIn(0, 2)
    private var duration = store.minutes * 60.0
    private var base = 0.0
    private var seed = Random.nextInt()
    override var stationMarks = FloatArray(0)
        private set

    init { configure(store.minutes) }

    override fun configure(minutes: Int) {
        duration = minutes.coerceAtLeast(1) * 60.0
        scene.durationSeconds = duration
        scene.course = course
        scene.direction = direction
        scene.eventSeed = seed
        val marks = course.waypointMarks(duration)
        stationMarks = if (direction > 0) marks else FloatArray(marks.size) { 1f - marks[marks.lastIndex - it] }
    }
    override fun selectCourse(id: String, minutes: Int) {
        course = catalog.first { it.id == id }
        direction = 1
        base = 0.0
        configure(minutes)
        frame(0.0, 0.0, SessionPhase.DOCKED, 0f)
    }
    override fun restore(active: JourneyStore.Active) {
        require(active.base.isFinite() && active.base >= 0)
        base = active.base
        seed = active.weatherSeed
        scene.eventSeed = seed
    }
    override fun onSessionEvent(event: SessionEvent, state: SessionState) {
        when (event) {
            is SessionEvent.Departed -> {
                if (event.from == SessionPhase.ARRIVED || event.from == SessionPhase.RESTING) returnTrip()
                seed = Random.nextInt()
                configure(state.tripMinutes)
            }
            is SessionEvent.GotOff, is SessionEvent.RestFinished -> { returnTrip(); configure(state.tripMinutes) }
            is SessionEvent.Ended -> configure(state.tripMinutes)
            else -> Unit
        }
    }
    private fun returnTrip() { base += 1.0; direction = -direction }
    override fun checkpoint(snapshot: SessionSnapshot, sessionId: String) = JourneyStore.Active(
        snapshot.state.phase.name, snapshot.state.tripMinutes, base, snapshot.clock,
        snapshot.state.restEndElapsedMillis, snapshot.state.restDone, seed, sessionId,
    )
    override fun frame(seconds: Double, rate: Double, phase: SessionPhase, dt: Float) {
        val p = (seconds / duration).toFloat().coerceIn(0f, 1f)
        scene.progress = if (phase == SessionPhase.ARRIVED || phase == SessionPhase.RESTING) 1f else p
        scene.distance = base + scene.progress
        scene.direction = direction
        scene.travelSpeed = if (phase == SessionPhase.RUNNING) rate.coerceAtLeast(0.0) else 0.0
        scene.resolveTime()
        running = phase == SessionPhase.RUNNING
        audio?.update(scene, running)
    }
    override fun reachedStations(seconds: Double) = stationMarks.indices
        .filter { seconds >= arrivalAt(it) }
        .map { if (direction > 0) it else course.stations.lastIndex - it }.toSet()
    override fun routeCaptionAt(seconds: Double): RouteCaption? {
        val p = (seconds / duration).toFloat().coerceIn(0f, 1f)
        val ordered = course.ordered(direction)
        if (course.flyby) {
            val position = if (direction > 0) course.positionAt(p, duration)
                else ordered.lastIndex - course.positionAt(1f - p, duration)
            val closest = kotlin.math.round(position).toInt().coerceIn(ordered.indices)
            return when {
                position < .12f -> RouteCaption(ordered.first().name, RouteCaption.Kind.DEPARTING)
                position > ordered.lastIndex - .15f -> RouteCaption(ordered.last().name, RouteCaption.Kind.APPROACHING)
                closest in 1 until ordered.lastIndex && kotlin.math.abs(position - closest) < .12f ->
                    RouteCaption(ordered[closest].name, RouteCaption.Kind.FLYBY)
                else -> null
            }
        }
        return when {
            p < .08f -> RouteCaption(ordered.first().name, RouteCaption.Kind.DEPARTING)
            p > .9f -> RouteCaption(ordered.last().name, RouteCaption.Kind.APPROACHING)
            else -> RouteCaption(ordered[if (ordered.size <= 2) 1 else (p * ordered.lastIndex).toInt().coerceIn(1, ordered.lastIndex - 1)].name, RouteCaption.Kind.PASSING)
        }
    }
    override fun stoppedAt(seconds: Double) = -1
    override fun nextStop(seconds: Double) = (stationMarks.indices.firstOrNull { seconds < arrivalAt(it) }
        ?: stationMarks.lastIndex).coerceAtLeast(1)
    // Float progress marks must not turn an exact 12-second countdown into 13 after ceiling.
    override fun arrivalAt(station: Int) =
        (stationMarks[station.coerceIn(0, stationMarks.lastIndex)] * duration * 1000).roundToLong() / 1000.0
    override fun secondsAtPosition(position: Double): Double {
        val p = course.progressAtPosition(position, duration)
        return (if (direction < 0) 1.0 - p else p) * duration
    }
    override fun setVisible(visible: Boolean, sound: Boolean) {
        if (visible && sound) (audio ?: makeAudio().also { audio = it; it.update(scene, running) }).start(level)
        else audio?.pause()
    }
    override fun setSoundLevel(level: Int) { this.level = level.coerceIn(0, 2); audio?.setLevel(this.level) }
    override fun chime() = Unit
    override fun selectSeasonChoice(choice: SeasonChoice) = Unit
    override fun selectTime(time: TimeOfDay?) { scene.fixedTime = time; scene.resolveTime() }
    override fun selectWeather(weather: Weather?) = Unit
    override fun selectLand(kind: SceneKind?) = Unit
    override fun selectMode(mode: RenderMode) = Unit
    override fun setBoxes(enabled: Boolean) = Unit
    override fun setWindowFrame(enabled: Boolean) { scene.windowFrame = enabled }
    override fun releasePresentation() { audio?.close(); audio = null; drawing?.release(); drawing = null }
}
