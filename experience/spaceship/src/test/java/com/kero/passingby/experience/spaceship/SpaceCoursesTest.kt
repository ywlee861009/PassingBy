package com.kero.passingby.experience.spaceship

import org.junit.Assert.*
import org.junit.Test

class SpaceCoursesTest {
    @Test fun everyRouteDepartsEarthAndContainsOnlyItsRealCelestialWaypoints() {
        val courses = SpaceCourses.all
        assertEquals(11, courses.size)
        assertEquals(courses.size, courses.map { it.id }.distinct().size)
        for (course in courses) {
            assertEquals(CelestialBody.EARTH, course.bodies.first())
            assertEquals(course.bodies.map { it.nameResource }, course.stations.map { it.name })
            assertFalse(course.supportsSeasons)
            assertFalse(course.supportsTimeOfDay)
            assertFalse(course.stopsEnRoute)
            assertTrue(course.references.isNotEmpty())
            assertTrue(course.references.all { it.url.startsWith("https://") })
        }
        assertEquals(CelestialBody.entries.toSet(), courses.flatMap { it.bodies }.toSet())
    }

    @Test fun requestedMarsUranusAndJupiterPlutoRoutesAreSeparateItineraries() {
        val uranus = SpaceCourses.all.first { it.id == "space_mars_uranus" }
        assertTrue(CelestialBody.MARS in uranus.bodies)
        assertEquals(CelestialBody.URANUS, uranus.bodies.last())
        val pluto = SpaceCourses.all.first { it.id == "space_jupiter_pluto" }
        assertTrue(CelestialBody.JUPITER in pluto.bodies)
        assertEquals(CelestialBody.PLUTO, pluto.bodies.last())
    }
}
