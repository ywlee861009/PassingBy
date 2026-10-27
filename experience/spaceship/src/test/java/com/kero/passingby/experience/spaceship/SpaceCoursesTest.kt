package com.kero.passingby.experience.spaceship

import org.junit.Assert.*
import org.junit.Test

class SpaceCoursesTest {
    @Test fun oneEscapeRouteHasFourVisitsThroughPlutoBeforeLeavingThemBehind() {
        val course = SpaceCourses.all.single()
        assertEquals("space_solar_escape", course.id)
        assertEquals(listOf(CelestialBody.EARTH, CelestialBody.MOON, CelestialBody.JUPITER,
            CelestialBody.SATURN, CelestialBody.PLUTO), course.bodies)
        assertEquals(4, course.stations.drop(1).dropLast(1).size)
        assertEquals(course.bodies.map { it.nameResource }, course.stations.dropLast(1).map { it.name })
        assertEquals(R.string.space_exit, course.stations.last().name)
        assertTrue(course.flyby)
        assertFalse(course.supportsSeasons)
        assertFalse(course.supportsTimeOfDay)
        assertFalse(course.stopsEnRoute)
        assertTrue(course.references.isNotEmpty())
        val marks = course.waypointMarks(1500.0)
        assertEquals(6, marks.size)
        assertEquals(1f, marks.last(), 0f)
        assertTrue("Pluto is a flyby before the destination", marks[course.bodies.lastIndex] < 1f)
        marks.forEachIndexed { index, progress ->
            assertEquals(index.toFloat(), course.positionAt(progress, 1500.0), .0001f)
        }
    }

    @Test fun allPreviousRoutesResolveToTheSingleEscapeCourseWithoutAddingPickerEntries() {
        val provider = SpaceExperienceProvider()
        assertEquals(1, provider.courses.size)
        assertEquals(11, provider.courseAliases.size)
        assertEquals(setOf("space_solar_escape"), provider.courseAliases.values.toSet())
        assertFalse(provider.courses.single().id in provider.courseAliases)
    }
}
