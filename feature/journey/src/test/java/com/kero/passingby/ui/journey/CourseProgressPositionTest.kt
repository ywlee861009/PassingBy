package com.kero.passingby.ui.journey

import org.junit.Assert.assertEquals
import org.junit.Test

class CourseProgressPositionTest {
    @Test fun aTwelveSecondMoonArrivalGetsItsOwnNodeInEverySessionLength() {
        for (minutes in listOf(25, 50, 90)) {
            val moon = 12f / (minutes * 60)
            val marks = floatArrayOf(0f, moon, moon + (1f - moon) / 3,
                moon + (1f - moon) * 2 / 3, 1f)
            marks.forEachIndexed { index, progress ->
                assertEquals(index / 4f, courseMarkerPosition(marks, progress), .00001f)
            }
            assertEquals(.125f, courseMarkerPosition(marks, moon / 2), .00001f)
            val betweenJupiterAndSaturn = (marks[2] + marks[3]) / 2
            assertEquals(.625f, courseMarkerPosition(marks, betweenJupiterAndSaturn), .00001f)
        }
    }

    @Test fun returningAlongAnUnevenTimetableMirrorsTheOutboundMarker() {
        val outbound = floatArrayOf(0f, .008f, .3386667f, .6693333f, 1f)
        val returning = FloatArray(outbound.size) { 1f - outbound[outbound.lastIndex - it] }
        for (step in 0..1000) {
            val p = step / 1000f
            assertEquals(1f - courseMarkerPosition(outbound, 1f - p),
                courseMarkerPosition(returning, p), .00001f)
        }
    }

    @Test fun twoStopRoutesAndEndpointsRetainTheirProgress() {
        val marks = floatArrayOf(0f, 1f)
        for (step in 0..100) {
            val p = step / 100f
            assertEquals(p, courseMarkerPosition(marks, p), 0f)
        }
        assertEquals(0f, courseMarkerPosition(marks, -1f), 0f)
        assertEquals(1f, courseMarkerPosition(marks, 2f), 0f)
    }
}
