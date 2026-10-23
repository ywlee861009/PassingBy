package com.kero.passingby.experience

import com.kero.passingby.audio.AmbientSound
import com.kero.passingby.content.Course
import com.kero.passingby.journey.CoursePlan
import com.kero.passingby.journey.CourseZones
import com.kero.passingby.journey.SessionEvent
import com.kero.passingby.journey.SessionPhase
import com.kero.passingby.journey.SessionSnapshot
import com.kero.passingby.journey.SessionState
import com.kero.passingby.journey.WeatherDirector
import com.kero.passingby.scene.*
import com.kero.passingby.session.JourneyStore
import kotlin.random.Random

class TrainExperienceProvider : ExperienceProvider {
    override val id = "train"
    override val courses: List<JourneyCourse> get() = Course.ALL
    override fun create(store: JourneyStore): ExperienceProgram = TrainProgram(store)
}

/** All rail planning, terrain, weather, rendering and audio stay inside the train implementation. */
internal class TrainProgram(private val store: JourneyStore) : ExperienceProgram {
    override var course = Course.byId(store.courseId)
        private set
    override val courseId get() = course.id
    override var direction = if (store.direction < 0) -1 else 1
        private set
    override val scene = SceneState()
    private var drawing: ExperienceRenderer? = null
    override val renderer: ExperienceRenderer get() = drawing ?: SceneRenderer(scene).also { drawing = it }
    private var audio: AmbientSound? = null
    private var level = store.soundLevel.coerceIn(0, 2)
    private var baseDistance = 0.0
    private var weatherSeed = Random.nextInt()
    private var choice = SeasonChoice.fromId(store.seasonChoiceId)
    private var plan = CoursePlan(store.minutes * 60.0, course.stations.size, course.cruise)
    private var weather = WeatherDirector(weatherSeed, course.snowy, choice.resolve(weatherSeed))
    private val planField = UrbanField { x -> plan.at(x - baseDistance) }
    private var land: SceneKind? = null
    override var stationMarks = FloatArray(0)
        private set

    init {
        scene.season = choice.resolve(weatherSeed)
        scene.setTimeOfDay(TimeOfDay.fromSetting(store.timeOfDayId))
        scene.snapTimeOfDay()
        scene.urban = planField
        configure(store.minutes)
    }

    override fun configure(minutes: Int) {
        plan = CoursePlan(minutes * 60.0, course.stations.size, course.cruise)
        weather = WeatherDirector(weatherSeed, course.snowy, scene.season)
        scene.theme = course.theme
        scene.snowy = course.snowy
        scene.zones = CourseZones(plan, course, direction, baseDistance)
        val stops = course.ordered(direction)
        scene.setStations(DoubleArray(stops.size) { baseDistance + plan.stationDistance(it) },
            Array(stops.size) { stops[it].local }, Array(stops.size) { stops[it].roman })
        stationMarks = FloatArray(stops.size) { when (it) {
            0 -> 0f
            stops.lastIndex -> 1f
            else -> (plan.arrivalAt(it) / plan.durationSec).toFloat()
        } }
    }

    override fun selectCourse(id: String, minutes: Int) {
        course = Course.ALL.first { it.id == id }
        direction = 1
        baseDistance = 0.0
        configure(minutes)
    }

    override fun restore(active: JourneyStore.Active) {
        require(active.base.isFinite() && active.base >= 0)
        baseDistance = active.base
        weatherSeed = active.weatherSeed
        scene.season = choice.resolve(weatherSeed)
    }

    override fun onSessionEvent(event: SessionEvent, state: SessionState) {
        when (event) {
            is SessionEvent.Departed -> {
                if (event.from == SessionPhase.ARRIVED || event.from == SessionPhase.RESTING) prepareReturn()
                weatherSeed = Random.nextInt()
                scene.season = choice.resolve(weatherSeed)
                configure(state.tripMinutes)
            }
            is SessionEvent.GotOff, is SessionEvent.RestFinished -> { prepareReturn(); configure(state.tripMinutes) }
            is SessionEvent.Ended -> configure(state.tripMinutes)
            else -> Unit
        }
    }

    private fun prepareReturn() { baseDistance += plan.totalDistance; direction = -direction }

    override fun checkpoint(snapshot: SessionSnapshot, sessionId: String) = JourneyStore.Active(
        snapshot.state.phase.name, snapshot.state.tripMinutes, baseDistance, snapshot.clock,
        snapshot.state.restEndElapsedMillis, snapshot.state.restDone, weatherSeed, sessionId)

    override fun reachedStations(seconds: Double): Set<Int> = course.stations.indices
        .filter { it == 0 || seconds >= plan.arrivalAt(it) }
        .map { if (direction > 0) it else course.stations.lastIndex - it }.toSet()

    override fun frame(seconds: Double, rate: Double, phase: SessionPhase, dt: Float) {
        val t = seconds.coerceIn(0.0, plan.durationSec)
        scene.setTravel(baseDistance + plan.distanceAt(t), plan.speedAt(t) * rate)
        scene.autoRain = weather.rainAt(t)
        scene.autoSnow = weather.snowAt(t)
        val sky = if (phase == SessionPhase.ARRIVED || phase == SessionPhase.RESTING) 1f else (t / plan.durationSec).toFloat()
        scene.update(dt, sky)
        audio?.let {
            it.speed = (scene.speed / course.cruise).toFloat()
            it.rain = scene.rainAmount
            it.tunnel = if (inTunnel(scene.distanceU)) 1f else 0f
            it.clack = if (course.cruise < Course.FAST) .9f else .45f
        }
    }

    private fun inTunnel(x: Double): Boolean {
        val ranges = scene.zones.tunnels
        var i = 0
        while (i < ranges.size) { if (x >= ranges[i] && x <= ranges[i + 1]) return true; i += 2 }
        return false
    }

    override fun routeCaptionAt(seconds: Double): RouteCaption? = course.guide.captionAt(
        plan.distanceAt(seconds) / plan.segDist, direction, plan.stoppedAt(seconds),
    )

    override fun stoppedAt(seconds: Double) = plan.stoppedAt(seconds)
    override fun nextStop(seconds: Double): Int {
        val stopped = plan.stoppedAt(seconds)
        return (if (stopped >= 0) stopped + 1 else plan.segmentAt(seconds) + 1).coerceIn(1, plan.segments)
    }
    override fun arrivalAt(station: Int) = plan.arrivalAt(station.coerceIn(1, plan.segments))
    override fun secondsAtPosition(position: Double): Double {
        val p = position.coerceIn(0.0, plan.segments.toDouble())
        val target = (if (direction < 0) plan.segments - p else p) * plan.segDist
        var lo = 0.0
        var hi = plan.durationSec
        repeat(60) { val mid = (lo + hi) / 2; if (plan.distanceAt(mid) < target) lo = mid else hi = mid }
        return hi
    }

    override fun setVisible(visible: Boolean, sound: Boolean) {
        if (visible && sound) {
            val current = audio ?: AmbientSound().also { audio = it; it.volume = LEVELS[level] }
            current.start()
        } else audio?.stop()
    }
    override fun setSoundLevel(level: Int) { this.level = level.coerceIn(0, 2); audio?.volume = LEVELS[this.level] }
    override fun chime() { audio?.chime() }
    override fun selectSeasonChoice(choice: SeasonChoice) {
        this.choice = choice
        scene.season = choice.resolve(weatherSeed)
        selectWeather(null)
        weather = WeatherDirector(weatherSeed, course.snowy, scene.season)
    }
    override fun selectTime(time: TimeOfDay?) { scene.setTimeOfDay(time) }
    override fun selectWeather(weather: Weather?) { scene.autoWeather = weather == null; if (weather != null) scene.weather = weather }
    override fun selectLand(kind: SceneKind?) { land = kind; scene.urban = kind?.field ?: planField }
    override fun selectMode(mode: RenderMode) { scene.mode = mode }
    override fun setBoxes(enabled: Boolean) { scene.showBoxes = enabled }
    override fun setWindowFrame(enabled: Boolean) { scene.windowFrame = enabled }
    override fun releasePresentation() { audio?.stop(); audio = null; drawing?.release(); drawing = null }

    private companion object { val LEVELS = floatArrayOf(.35f, .6f, .9f) }
}
