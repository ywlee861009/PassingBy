package com.kero.passingby.journey

import org.junit.Assert.*
import org.junit.Test

class JourneyClockTest {
    @Test fun timeAdvancesWithoutRenderingAndSurvivesRecreation() {
        var now = 1_000L
        val clock = JourneyClock { now }.apply { seek(0.0, 1.0) }
        now += 10_000
        assertEquals(10.0, clock.seconds(), 1e-9)
        val snapshot = clock.save()
        now += 5_000
        val restored = JourneyClock { now }.apply { restore(snapshot) }
        assertEquals(15.0, restored.seconds(), 1e-9)
        assertEquals(1.0, restored.rate(), 1e-9)
    }

    @Test fun smoothPauseSettlesAndRemainsPaused() {
        var now = 0L
        val clock = JourneyClock { now }.apply { seek(20.0, 1.0) }
        clock.setRate(0.0, 3.5)
        assertEquals(20.0, clock.seconds(), 1e-9)
        now += 3_500
        assertEquals(21.75, clock.seconds(), 1e-9)
        assertEquals(0.0, clock.rate(), 1e-9)
        now += 120_000
        assertEquals(21.75, clock.seconds(), 1e-9)
        clock.setRate(1.0, 3.5)
        now += 3_500
        assertEquals(23.5, clock.seconds(), 1e-9)
        assertEquals(1.0, clock.rate(), 1e-9)
    }

    @Test fun restoringDuringDecelerationPreservesPositionAndRate() {
        var now = 0L
        val clock = JourneyClock { now }.apply { seek(100.0, 1.0); setRate(0.0, 3.5) }
        now = 1_200
        val restored = JourneyClock { now }.apply { restore(clock.save()) }
        for (instant in listOf(1_200L, 1_750L, 3_500L, 90_000L)) {
            now = instant
            assertEquals(clock.seconds(), restored.seconds(), 1e-9)
            assertEquals(clock.rate(), restored.rate(), 1e-9)
        }
    }

    @Test fun changingScaleDoesNotJumpAndAffectsSubsequentTime() {
        var now = 0L
        val clock = JourneyClock { now }.apply { seek(0.0, 1.0) }
        now = 5_000
        val before = clock.seconds()
        clock.setScale(2.0)
        assertEquals(before, clock.seconds(), 1e-9)
        now += 5_000
        assertEquals(before + 10.0, clock.seconds(), 1e-9)
    }

    @Test fun resumingPartwayThroughPauseHasNoPositionDiscontinuity() {
        var now = 0L
        val clock = JourneyClock { now }.apply { seek(0.0, 1.0); setRate(0.0, 3.5) }
        now = 1_000
        val before = clock.seconds()
        val rateBefore = clock.rate()
        clock.setRate(1.0, 3.5)
        assertEquals(before, clock.seconds(), 1e-9)
        assertEquals(rateBefore, clock.rate(), 1e-9)
        now += 3_500
        assertTrue(clock.seconds() > before)
        assertEquals(1.0, clock.rate(), 1e-9)
    }
}
