package com.kero.passingby.experience

import android.content.res.Resources
import com.kero.passingby.experience.api.R
import com.kero.passingby.journey.JourneyProgram
import com.kero.passingby.journey.SessionPhase
import com.kero.passingby.session.JourneyStore

interface JourneyStation { val name: Int; val local: String; val roman: String }

/** UI catalog metadata, without terrain, audio or rendering data. */
interface JourneyCourse {
    val id: String
    val number: Int
    val region: Int
    val vehicle: Int
    val stations: List<JourneyStation>
    val productId: String?
    val free: Boolean get() = productId == null
    fun ordered(direction: Int): List<JourneyStation> = if (direction < 0) stations.asReversed() else stations
    fun title(res: Resources, direction: Int): String {
        val stops = ordered(direction)
        return "${res.getString(stops.first().name)} → ${res.getString(stops.last().name)}"
    }
    fun stopsLine(res: Resources, direction: Int): String {
        val middle = ordered(direction).drop(1).dropLast(1)
        return if (middle.isEmpty()) res.getString(R.string.nonstop)
        else res.getString(R.string.stops_line, middle.joinToString(" · ") { res.getString(it.name) })
    }
}

/** Read-only presentation data. The experience keeps its mutable rendering state private. */
interface ExperienceScene {
    val distanceU: Double
    val speed: Double
    val season: Season
    val timeOfDay: TimeOfDay
    val autoWeather: Boolean
    val rainAmount: Float
    val snowAmount: Float
    val panelColor: Int
    val urbanDensity: Float
    fun paperColor(baseColor: Int): Int
    fun setViewport(fromEnd: Boolean, edgePx: Float)
    fun snapTimeOfDay()
    fun snapWeather()
}

interface ExperienceProgram : JourneyProgram, AutoCloseable {
    val course: JourneyCourse
    val scene: ExperienceScene
    val renderer: ExperienceRenderer
    val stationMarks: FloatArray
    fun frame(seconds: Double, rate: Double, phase: SessionPhase, dt: Float)
    fun stoppedAt(seconds: Double): Int
    fun nextStop(seconds: Double): Int
    fun arrivalAt(station: Int): Double
    fun secondsAtPosition(position: Double): Double
    fun setVisible(visible: Boolean, sound: Boolean)
    fun setSoundLevel(level: Int)
    fun chime()
    fun selectSeasonChoice(choice: SeasonChoice)
    fun selectTime(time: TimeOfDay?)
    fun selectWeather(weather: Weather?)
    fun selectLand(kind: SceneKind?)
    fun selectMode(mode: RenderMode)
    fun setBoxes(enabled: Boolean)
    fun setWindowFrame(enabled: Boolean)
    /** Releases UI resources; route/session state remains available to the headless runtime. */
    fun releasePresentation()
    override fun close() = releasePresentation()
}

interface ExperienceProvider {
    val id: String
    val courses: List<JourneyCourse>
    fun create(store: JourneyStore): ExperienceProgram
}

class ExperienceRegistry(providers: List<ExperienceProvider>) {
    private val providers = providers.toList()
    val courses = this.providers.flatMap { it.courses }
    init {
        require(this.providers.isNotEmpty())
        require(this.providers.all { it.courses.isNotEmpty() })
        require(this.providers.map { it.id }.distinct().size == this.providers.size)
        require(courses.isNotEmpty() && courses.map { it.id }.distinct().size == courses.size)
    }
    fun providerFor(courseId: String?): ExperienceProvider = providers.firstOrNull { p -> p.courses.any { it.id == courseId } } ?: providers.first()
    fun courseById(id: String?): JourneyCourse = courses.firstOrNull { it.id == id } ?: courses.first()
    fun create(courseId: String?, store: JourneyStore): ExperienceProgram = providerFor(courseId).create(store)
}
