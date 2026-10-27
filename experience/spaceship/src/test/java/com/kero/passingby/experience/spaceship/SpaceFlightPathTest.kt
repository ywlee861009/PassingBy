package com.kero.passingby.experience.spaceship

import org.junit.Assert.*
import org.junit.Test

class SpaceFlightPathTest {
    @Test fun launchBeginsAtGroundAndRisesBeforeAnySidewaysTravel() {
        val ground = SpaceFlightPath.sample(0f, 1500.0, 5)
        assertEquals(0f, ground.ascent, 0f)
        assertEquals(0f, ground.stars, 0f)
        val rising = SpaceFlightPath.sample(1f / 1500, 1500.0, 5)
        assertTrue(rising.ascent > 0f)
        assertEquals(0f, rising.turn, 0f)
        assertEquals(0f, rising.trackPosition, 0f)
        val turning = SpaceFlightPath.sample(3.5f / 1500, 1500.0, 5)
        assertEquals(1f, turning.ascent, 0f)
        assertTrue(turning.turn in .01f.. .99f)
    }

    @Test fun cameraMatchesTheFlybyTimetableAndIsIndependentOfFrameHistory() {
        for (count in 2..9) for (minutes in listOf(1, 25, 50, 90)) {
            val duration = minutes * 60.0
            for (i in 1 until count) {
                val p = SpaceFlightPath.markAt(i, duration, count)
                assertEquals(i.toFloat(), SpaceFlightPath.sample(p, duration, count).trackPosition, .00001f)
            }
            val before = SpaceFlightPath.sample(.031f, duration, count)
            SpaceFlightPath.sample(.9f, duration, count)
            assertEquals(before, SpaceFlightPath.sample(.031f, duration, count))
        }
    }

    @Test fun launchClearsEarthInFourSecondsAndReachesTheMoonInTwelveSeconds() {
        for (minutes in listOf(25, 50, 90)) for (count in 4..9) {
            val duration = minutes * 60.0
            assertEquals(1f, SpaceFlightPath.sample(3f / duration.toFloat(), duration, count).ascent, .0001f)
            assertEquals(1f, SpaceFlightPath.sample(4f / duration.toFloat(), duration, count).turn, .0001f)
            assertEquals(12.0, SpaceFlightPath.markAt(1, duration, count) * duration, .0001)
            assertEquals(1f, SpaceFlightPath.sample(12f / duration.toFloat(), duration, count).trackPosition, .0001f)
        }
    }

    @Test fun planetsClearTheWindowWithinSecondsRegardlessOfFocusDuration() {
        for (minutes in listOf(25, 50, 90)) {
            val duration = minutes * 60.0
            for (i in 1..7) {
                val encounter = SpaceFlightPath.markAt(i, duration, 9) * duration
                val before = SpaceFlightPath.sample(((encounter - 8) / duration).toFloat(), duration, 9)
                val after = SpaceFlightPath.sample(((encounter + 8) / duration).toFloat(), duration, 9)
                assertTrue(before.trackPosition <= i - .399f)
                assertTrue(after.trackPosition >= i + .399f)
            }
            assertTrue(SpaceFlightPath.sample((6 / duration).toFloat(), duration, 9).trackPosition >= .399f)
            assertEquals(8f, SpaceFlightPath.sample(1f, duration, 9).trackPosition, .0001f)
        }
    }

    @Test fun seekingUsesTheInverseOfTheAcceleratedCameraPath() {
        for (duration in listOf(60.0, 1500.0, 5400.0)) for (step in 0..80) {
            val position = step / 10.0
            val progress = SpaceFlightPath.progressAtPosition(position, duration, 9)
            assertEquals(position.toFloat(), SpaceFlightPath.sample(progress.toFloat(), duration, 9).trackPosition, .0005f)
        }
    }

    @Test fun cameraStaysContinuousAcrossLaunchAndFlybysInBothDirections() {
        var previous = SpaceFlightPath.sample(0f, 1500.0, 5)
        for (i in 1..20000) {
            val current = SpaceFlightPath.sample(i / 20000f, 1500.0, 5)
            assertTrue(current.ascent >= previous.ascent)
            assertTrue(current.turn >= previous.turn)
            assertTrue(current.trackPosition >= previous.trackPosition)
            assertTrue(current.trackPosition - previous.trackPosition < .03f)
            previous = current
        }
        assertEquals(0f, SpaceFlightPath.sample(1f - 1f, 1500.0, 5).ascent, 0f)
    }
}
