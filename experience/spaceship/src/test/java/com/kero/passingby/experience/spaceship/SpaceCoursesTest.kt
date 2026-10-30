package com.kero.passingby.experience.spaceship

import org.junit.Assert.*
import org.junit.Test

class SpaceCoursesTest {
    @Test fun blackHoleCourseEndsAtItsOwnDestination() {
        val course = SpaceCourses.all.single { it.id == "space_black_hole" }
        assertEquals(CelestialBody.EARTH, course.bodies.first())
        assertEquals(CelestialBody.BLACK_HOLE, course.bodies.last())
        assertEquals(R.string.body_black_hole, course.stations.last().name)
        for (duration in listOf(1500.0, 3000.0, 5400.0)) {
            assertEquals(1f, course.waypointMarks(duration).last(), 0f)
            assertEquals(course.bodies.lastIndex.toFloat(), course.positionAt(1f, duration), .0001f)
        }
    }

    @Test fun currentRouteEndsAtPlutoAtTheEndOfTheFocusSession() {
        val course = SpaceCourses.all.first()
        assertEquals("space_solar_escape", course.id)
        assertEquals(listOf(CelestialBody.EARTH, CelestialBody.MOON, CelestialBody.JUPITER,
            CelestialBody.SATURN, CelestialBody.PLUTO), course.bodies)
        assertEquals(3, course.stations.drop(1).dropLast(1).size)
        assertEquals(course.bodies.map { it.nameResource }, course.stations.map { it.name })
        assertEquals(R.string.body_pluto, course.stations.last().name)
        assertTrue(course.flyby)
        assertFalse(course.supportsSeasons)
        assertFalse(course.supportsTimeOfDay)
        assertFalse(course.stopsEnRoute)
        assertTrue(course.references.isNotEmpty())
        for (duration in listOf(1500.0, 3000.0, 5400.0)) {
            val marks = course.waypointMarks(duration)
            assertEquals(5, marks.size)
            assertEquals(1f, marks.last(), 0f)
            assertEquals(12.0, marks[1] * duration, .001)
            marks.forEachIndexed { index, progress ->
                assertEquals(index.toFloat(), course.positionAt(progress, duration), .0001f)
            }
        }
    }

    @Test fun aFutureCourseUsesItsOwnFinalBodyWithoutAppendingAnotherDestination() {
        val course = SpaceCourse("test_earth_mars", 102, R.string.body_mars,
            listOf(CelestialBody.EARTH, CelestialBody.MARS), 0xff6b7794.toInt(), R.string.route_pluto)
        assertEquals(listOf(R.string.body_earth, R.string.body_mars), course.stations.map { it.name })
        assertEquals(listOf(0f, 1f), course.waypointMarks(1500.0).toList())
        assertEquals(1f, course.positionAt(1f, 1500.0), 0f)
    }

    @Test fun allPreviousRoutesStillResolveWithoutChangingSavedCourseIdsOrAddingPickerEntries() {
        val provider = SpaceExperienceProvider()
        assertEquals(2, provider.courses.size)
        assertEquals(11, provider.courseAliases.size)
        assertEquals(setOf("space_solar_escape"), provider.courseAliases.values.toSet())
        assertFalse(provider.courses.first().id in provider.courseAliases)
    }
}
