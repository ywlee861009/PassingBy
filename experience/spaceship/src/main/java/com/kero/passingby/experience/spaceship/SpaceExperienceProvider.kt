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
    bodies.map { VoyageStop(it.nameResource, it.roman, it.roman) }, supportsTimeOfDay = false) {
    init { require(bodies.first() == CelestialBody.EARTH); require(bodies.distinct().size == bodies.size) }
    override val flyby = true
    override fun positionAt(progress: Float, durationSeconds: Double) =
        SpaceFlightPath.sample(progress, durationSeconds, bodies.size).trackPosition
    override fun waypointMarks(durationSeconds: Double) =
        FloatArray(bodies.size) { SpaceFlightPath.markAt(it, durationSeconds, bodies.size) }
}

object SpaceCourses {
    val all = listOf(
        SpaceCourse("space_earth_moon", 101, R.string.space_lunar_route,
            listOf(CelestialBody.EARTH, CelestialBody.MOON, CelestialBody.MARS, CelestialBody.JUPITER), 0xff467c9a.toInt(), R.string.route_lunar),
        SpaceCourse("space_inner_planets", 102, R.string.space_inner_route,
            listOf(CelestialBody.EARTH, CelestialBody.MOON, CelestialBody.VENUS, CelestialBody.MERCURY), 0xff9a8466.toInt(), R.string.route_inner),
        SpaceCourse("space_earth_mars", 103, R.string.space_mars_route,
            listOf(CelestialBody.EARTH, CelestialBody.MOON, CelestialBody.VENUS, CelestialBody.MARS), 0xff95616e.toInt(), R.string.route_mars),
        SpaceCourse("space_mars_uranus", 104, R.string.space_uranus_route,
            listOf(CelestialBody.EARTH, CelestialBody.MOON, CelestialBody.MARS, CelestialBody.JUPITER, CelestialBody.SATURN, CelestialBody.URANUS), 0xff477b8a.toInt(), R.string.route_uranus),
        SpaceCourse("space_jupiter_pluto", 105, R.string.space_pluto_route,
            listOf(CelestialBody.EARTH, CelestialBody.MOON, CelestialBody.MARS, CelestialBody.JUPITER, CelestialBody.PLUTO), 0xff756d91.toInt(), R.string.route_pluto,
            references = listOf(CourseReference("Stern et al. · Science (2015)", "https://arxiv.org/abs/1510.07704"),
                CourseReference("NASA · New Horizons", "https://science.nasa.gov/mission/new-horizons/"))),
        SpaceCourse("space_grand_tour", 106, R.string.space_grand_route,
            listOf(CelestialBody.EARTH, CelestialBody.MOON, CelestialBody.JUPITER, CelestialBody.SATURN, CelestialBody.URANUS, CelestialBody.NEPTUNE),
            0xff6b7794.toInt(), R.string.route_grand,
            references = listOf(CourseReference("Stone & Miner · Science (1989)", "https://pds-rings.seti.org/voyager/reference/science_v2n/overview/overview.html"),
                CourseReference("NASA · Voyager 2", "https://science.nasa.gov/mission/voyager/voyager-2/"))),
        SpaceCourse("space_proxima", 107, R.string.space_proxima_route,
            listOf(CelestialBody.EARTH, CelestialBody.MOON, CelestialBody.JUPITER, CelestialBody.PROXIMA_B), 0xff806974.toInt(), R.string.route_proxima,
            R.string.space_proxima_note, 4.2439,
            listOf(CourseReference("Anglada-Escudé et al. · Nature (2016)", "https://arxiv.org/abs/1609.03449"))),
        SpaceCourse("space_trappist", 108, R.string.space_trappist_route,
            listOf(CelestialBody.EARTH, CelestialBody.MOON, CelestialBody.JUPITER, CelestialBody.TRAPPIST_E, CelestialBody.TRAPPIST_F, CelestialBody.TRAPPIST_G),
            0xff786b86.toInt(), R.string.route_trappist, R.string.space_trappist_note, 40.5409,
            listOf(CourseReference("Gillon et al. · Nature (2017)", "https://www.nature.com/articles/nature21360"))),
        SpaceCourse("space_lhs1140", 109, R.string.space_lhs_route,
            listOf(CelestialBody.EARTH, CelestialBody.MOON, CelestialBody.JUPITER, CelestialBody.LHS1140_B), 0xff5d7d8a.toInt(), R.string.route_lhs,
            R.string.space_lhs_note, 48.8779,
            listOf(CourseReference("Cadieux et al. · ApJL (2024)", "https://arxiv.org/abs/2406.15136"))),
        SpaceCourse("space_toi700", 110, R.string.space_toi_route,
            listOf(CelestialBody.EARTH, CelestialBody.MOON, CelestialBody.JUPITER, CelestialBody.TOI700_E, CelestialBody.TOI700_D), 0xff876a78.toInt(), R.string.route_toi,
            R.string.space_toi_note, 101.5188,
            listOf(CourseReference("Gilbert et al. · ApJL (2023)", "https://arxiv.org/abs/2301.03617"))),
        SpaceCourse("space_55cancri", 111, R.string.space_cancri_route,
            listOf(CelestialBody.EARTH, CelestialBody.MOON, CelestialBody.VENUS, CelestialBody.CANCRI_E), 0xffa16b53.toInt(), R.string.route_cancri,
            R.string.space_cancri_note, 41.0476,
            listOf(CourseReference("Hu et al. · Nature (2024)", "https://arxiv.org/abs/2405.04744"))),
    )
}

class SpaceExperienceProvider : ExperienceProvider {
    override val id = "spaceship"
    override val mode = JourneyMode.SPACESHIP
    override val courses: List<JourneyCourse> = SpaceCourses.all
    override fun create(store: JourneyStore) = VoyageProgram(store, SpaceCourses.all, { scene -> SpaceRenderer(scene) }, AmbientLoop.Kind.SPACE, ::SpaceAudio)
}
