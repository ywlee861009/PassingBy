package com.kero.passingby.content

import com.kero.passingby.experience.RouteCaption
import com.kero.passingby.journey.CoursePlan
import org.junit.Assert.*
import org.junit.Test

class RouteGuideTest {
    private val guide = RouteGuide(
        stops = listOf(RoutePlace(1, 0.0), RoutePlace(2, 100.0), RoutePlace(3, 400.0)),
        via = listOf(RoutePlace(4, 20.0), RoutePlace(5, 80.0), RoutePlace(6, 150.0), RoutePlace(7, 350.0)),
    )

    @Test fun unequalRealSegmentsUseTheirOwnDistanceRatiosInBothDirections() {
        assertEquals(25.0, guide.kmAt(.25, 1), 1e-9)
        assertEquals(175.0, guide.kmAt(1.25, 1), 1e-9)
        assertEquals(325.0, guide.kmAt(.25, -1), 1e-9)
        assertEquals(75.0, guide.kmAt(1.25, -1), 1e-9)
        for (i in 0..200) {
            val position = i / 100.0
            assertEquals(guide.kmAt(position, 1), guide.kmAt(2 - position, -1), 1e-9)
        }
    }

    @Test fun captionsOnlyNameNearbyPlacesAndDistinguishLeavingArrivingAndStopping() {
        assertEquals(RouteCaption(1, RouteCaption.Kind.DEPARTING), guide.captionAt(0.0, 1))
        assertEquals(RouteCaption(4, RouteCaption.Kind.PASSING), guide.captionAt(.2, 1))
        assertNull(guide.captionAt(.5, 1))
        assertEquals(RouteCaption(2, RouteCaption.Kind.APPROACHING), guide.captionAt(.99, 1))
        assertEquals(RouteCaption(2, RouteCaption.Kind.STOPPED), guide.captionAt(1.0, 1, stoppedAt = 1))
        assertEquals(RouteCaption(2, RouteCaption.Kind.DEPARTING), guide.captionAt(1.0, 1))
        assertEquals(RouteCaption(3, RouteCaption.Kind.DEPARTING), guide.captionAt(0.0, -1))
        assertEquals(RouteCaption(7, RouteCaption.Kind.PASSING), guide.captionAt(1.0 / 6, -1))
        assertEquals(RouteCaption(2, RouteCaption.Kind.APPROACHING), guide.captionAt(.99, -1))
        assertEquals(RouteCaption(2, RouteCaption.Kind.STOPPED), guide.captionAt(1.0, -1, stoppedAt = 1))
        assertEquals(RouteCaption(2, RouteCaption.Kind.DEPARTING), guide.captionAt(1.0, -1))
        for (direction in listOf(-1, 1)) {
            assertNull(guide.captionAt(2.0, direction))
            assertNull(guide.captionAt(-.1, direction))
            assertNull(guide.captionAt(Double.NaN, direction))
        }
    }

    @Test fun everyCoursePassesEveryPlaceInOrderForShortLongAndReturnJourneys() {
        assertEquals(8, RouteGuides.ALL.size)
        for ((index, route) in RouteGuides.ALL.withIndex()) {
            assertTrue(route.via.isNotEmpty())
            for (segment in 0 until route.stops.lastIndex) {
                assertTrue(route.via.any { it.km > route.stops[segment].km && it.km < route.stops[segment + 1].km })
            }
            for (minutes in listOf(25, 50, 90)) for (direction in listOf(1, -1)) {
                val plan = CoursePlan(minutes * 60.0, route.stops.size, 300.0)
                val encountered = mutableListOf<Int>()
                for (second in 0..minutes * 60) {
                    val t = second.toDouble()
                    val caption = route.captionAt(plan.distanceAt(t) / plan.segDist, direction, plan.stoppedAt(t))
                    if (caption?.kind == RouteCaption.Kind.PASSING && encountered.lastOrNull() != caption.name) {
                        encountered += caption.name
                    }
                }
                val expected = (if (direction < 0) route.via.asReversed() else route.via).map { it.name }
                assertEquals("Course ${index + 1}, $minutes min, direction $direction", expected, encountered)
            }
        }
    }

    @Test fun elapsedTimeDeterminesTheSameCaptionAfterPauseOrRestoreIncludingStops() {
        for (route in RouteGuides.ALL) for (direction in listOf(1, -1)) {
            val plan = CoursePlan(1500.0, route.stops.size, 300.0)
            val restored = CoursePlan(1500.0, route.stops.size, 300.0)
            for (t in listOf(0.0, 75.0, 220.0, 610.0, 1100.0, 1499.0)) {
                val expected = route.captionAt(plan.distanceAt(t) / plan.segDist, direction, plan.stoppedAt(t))
                repeat(3) {
                    assertEquals(expected, route.captionAt(restored.distanceAt(t) / restored.segDist, direction, restored.stoppedAt(t)))
                }
            }
            for (stop in 1 until route.stops.lastIndex) {
                val t = plan.arrivalAt(stop) + plan.stopSec / 2
                val name = route.stops[if (direction < 0) route.stops.lastIndex - stop else stop].name
                assertEquals(RouteCaption(name, RouteCaption.Kind.STOPPED),
                    route.captionAt(plan.distanceAt(t) / plan.segDist, direction, plan.stoppedAt(t)))
            }
        }
    }

    @Test fun malformedRouteDataFailsBeforeItCanShowIncorrectLocations() {
        val stops = listOf(RoutePlace(1, 0.0), RoutePlace(2, 100.0))
        fun rejects(stations: List<RoutePlace>, passing: List<RoutePlace>) {
            try {
                RouteGuide(stations, passing)
                fail("Invalid route accepted")
            } catch (_: IllegalArgumentException) { }
        }
        rejects(stops.reversed(), emptyList())
        rejects(stops, listOf(RoutePlace(3, 101.0)))
        rejects(stops, listOf(RoutePlace(3, 0.0)))
        rejects(stops, listOf(RoutePlace(3, Double.NaN)))
        rejects(stops, listOf(RoutePlace(3, 60.0), RoutePlace(4, 40.0)))
        rejects(stops, listOf(RoutePlace(3, 40.0), RoutePlace(4, 40.0)))
    }
}
