package com.kero.passingby.experience.spaceship

import com.kero.passingby.experience.*
import com.kero.passingby.experience.voyage.*
import com.kero.passingby.session.JourneyStore

/** Real solar-system destinations. Visual scale and travel time follow the focus session. */
enum class CelestialBody(val nameResource: Int, val roman: String, val color: Int, val shade: Int) {
    EARTH(R.string.body_earth, "Earth", 0xff75b9d7.toInt(), 0xff153a65.toInt()),
    MOON(R.string.body_moon, "Moon", 0xffe1ddce.toInt(), 0xff555967.toInt()),
    MERCURY(R.string.body_mercury, "Mercury", 0xffb9aaa0.toInt(), 0xff514e53.toInt()),
    VENUS(R.string.body_venus, "Venus", 0xffedcf9b.toInt(), 0xff9d7358.toInt()),
    MARS(R.string.body_mars, "Mars", 0xffe4a17b.toInt(), 0xff753f40.toInt()),
    JUPITER(R.string.body_jupiter, "Jupiter", 0xffe9c7a0.toInt(), 0xff9a6b63.toInt()),
    SATURN(R.string.body_saturn, "Saturn", 0xffe4d5ad.toInt(), 0xff8c827b.toInt()),
    URANUS(R.string.body_uranus, "Uranus", 0xffa3dadb.toInt(), 0xff497b8c.toInt()),
    NEPTUNE(R.string.body_neptune, "Neptune", 0xff6aa5e1.toInt(), 0xff263c85.toInt()),
    PLUTO(R.string.body_pluto, "Pluto", 0xffc7b7a7.toInt(), 0xff706569.toInt()),
    PROXIMA_B(R.string.body_proxima_b, "Proxima Centauri b", 0xffb7a29a.toInt(), 0xff443c48.toInt()),
    TRAPPIST_E(R.string.body_trappist_e, "TRAPPIST-1 e", 0xffa7a29b.toInt(), 0xff51434c.toInt()),
    TRAPPIST_F(R.string.body_trappist_f, "TRAPPIST-1 f", 0xffc0c8cd.toInt(), 0xff566174.toInt()),
    TRAPPIST_G(R.string.body_trappist_g, "TRAPPIST-1 g", 0xffb6c4d0.toInt(), 0xff47566d.toInt()),
    LHS1140_B(R.string.body_lhs1140_b, "LHS 1140 b", 0xffced8dc.toInt(), 0xff526b7c.toInt()),
    TOI700_E(R.string.body_toi700_e, "TOI-700 e", 0xffb4a79c.toInt(), 0xff51434b.toInt()),
    TOI700_D(R.string.body_toi700_d, "TOI-700 d", 0xffb1b8ae.toInt(), 0xff4b5058.toInt()),
    CANCRI_E(R.string.body_cancri_e, "55 Cancri e", 0xffdf9057.toInt(), 0xff542d36.toInt()),

}

class SpaceCourse(
    id: String, number: Int, region: Int, val bodies: List<CelestialBody>,
    val glow: Int, override val description: Int,
    override val researchNote: Int = R.string.space_solar_note,
    override val distanceLightYears: Double? = null,
    override val references: List<CourseReference> = listOf(CourseReference("NASA · Solar system", "https://science.nasa.gov/solar-system/planets/")),
) : VoyageCourse(id, number, region, R.string.space_vehicle,
    bodies.map { VoyageStop(it.nameResource, it.roman, it.roman) } +
        VoyageStop(R.string.space_exit, "Interstellar space", "Interstellar space"), supportsTimeOfDay = false) {
    init { require(bodies.first() == CelestialBody.EARTH); require(bodies.distinct().size == bodies.size) }
    override val flyby = true
    override fun positionAt(progress: Float, durationSeconds: Double) =
        SpaceFlightPath.sample(progress, durationSeconds, stations.size).trackPosition
    override fun waypointMarks(durationSeconds: Double) =
        FloatArray(stations.size) { SpaceFlightPath.markAt(it, durationSeconds, stations.size) }
    override fun progressAtPosition(position: Double, durationSeconds: Double) =
        SpaceFlightPath.progressAtPosition(position, durationSeconds, stations.size)
}

object SpaceCourses {
    val all = listOf(
        SpaceCourse("space_solar_escape", 101, R.string.space_escape_route,
            listOf(CelestialBody.EARTH, CelestialBody.MOON, CelestialBody.JUPITER,
                CelestialBody.SATURN, CelestialBody.PLUTO),
            0xff6b7794.toInt(), R.string.route_escape),
    )
    val legacyIds = setOf("space_earth_moon", "space_inner_planets", "space_earth_mars", "space_mars_uranus",
        "space_jupiter_pluto", "space_grand_tour", "space_proxima", "space_trappist", "space_lhs1140", "space_toi700", "space_55cancri")
}

class SpaceExperienceProvider : ExperienceProvider {
    override val id = "spaceship"
    override val mode = JourneyMode.SPACESHIP
    override val courses: List<JourneyCourse> = SpaceCourses.all
    override val courseAliases = SpaceCourses.legacyIds.associateWith { SpaceCourses.all.single().id }
    override fun create(store: JourneyStore) = VoyageProgram(store, SpaceCourses.all, { scene -> SpaceRenderer(scene) }, AmbientLoop.Kind.SPACE, ::SpaceAudio)
}
